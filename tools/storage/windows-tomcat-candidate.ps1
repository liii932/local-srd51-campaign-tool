# Isolated native Windows external Tomcat acceptance. This is not a formal deployment tool.
# Keeps the installed CATALINA_HOME, existing webapps and real storage untouched.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Prepare', 'Start', 'Status', 'Stop')][string]$Action,
    [Parameter(Mandatory)][string]$CatalinaBase,
    [string]$StorageRoot,
    [string]$TomcatHome,
    [string]$JavaHome,
    [string]$War
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $IsWindows) { throw 'WINDOWS_REQUIRED' }

function Full-LocalPath([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value -notmatch '^[A-Za-z]:[\\/]' -or
            $Value.Contains('"') -or $Value.Contains("`r") -or $Value.Contains("`n")) {
        throw 'ABSOLUTE_LOCAL_PATH_REQUIRED'
    }
    $full = [IO.Path]::GetFullPath($Value)
    if ($full -eq [IO.Path]::GetPathRoot($full)) { throw 'DEDICATED_DIRECTORY_REQUIRED' }
    return $full.TrimEnd([char[]]'\/')
}

function Assert-NoReparse([string]$Value) {
    for ($cursor = $Value; $cursor; $cursor = [IO.Path]::GetDirectoryName($cursor)) {
        if (Test-Path -LiteralPath $cursor) {
            if ((Get-Item -Force -LiteralPath $cursor).Attributes -band [IO.FileAttributes]::ReparsePoint) {
                throw 'REPARSE_POINT_NOT_ALLOWED'
            }
        }
    }
}

function Contains-Path([string]$Parent, [string]$Child) {
    return $Parent.Equals($Child, [StringComparison]::OrdinalIgnoreCase) -or
        $Child.StartsWith($Parent + '\', [StringComparison]::OrdinalIgnoreCase)
}

function Assert-LocalNtfs([string]$Value) {
    try {
        $drive = [IO.DriveInfo]::new([IO.Path]::GetPathRoot($Value))
        # Check the mapping type before reading a filesystem format or target path.
        if ($drive.DriveType -ne [IO.DriveType]::Fixed) { throw 'LOCAL_NTFS_REQUIRED' }
        if ($drive.DriveFormat -ne 'NTFS') { throw 'LOCAL_NTFS_REQUIRED' }
    } catch { throw 'LOCAL_NTFS_REQUIRED' }
}

function Assert-EmptyTarget([string]$Value) {
    Assert-NoReparse $Value
    if (Test-Path -LiteralPath $Value) {
        if (-not (Test-Path -LiteralPath $Value -PathType Container) -or
                @(Get-ChildItem -Force -LiteralPath $Value).Count -ne 0) { throw 'TARGET_NOT_EMPTY' }
    }
    for ($cursor = $Value; $cursor; $cursor = [IO.Path]::GetDirectoryName($cursor)) {
        if ((Test-Path -LiteralPath (Join-Path $cursor '.git')) -or
                (Test-Path -LiteralPath (Join-Path $cursor 'WEB-INF'))) { throw 'TARGET_INSIDE_APPLICATION' }
    }
}

function New-PrivateDirectory([string]$Value) {
    [IO.Directory]::CreateDirectory($Value) | Out-Null
    $owner = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $security = [Security.AccessControl.DirectorySecurity]::new()
    $security.SetOwner($owner)
    $security.SetAccessRuleProtection($true, $false)
    $inheritance = [Security.AccessControl.InheritanceFlags]'ContainerInherit,ObjectInherit'
    $security.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($owner,
        [Security.AccessControl.FileSystemRights]::FullControl, $inheritance,
        [Security.AccessControl.PropagationFlags]::None, [Security.AccessControl.AccessControlType]::Allow))
    Set-Acl -LiteralPath $Value -AclObject $security
    $actual = Get-Acl -LiteralPath $Value
    $rules = @($actual.GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier]))
    if (-not $actual.AreAccessRulesProtected -or $actual.GetOwner([Security.Principal.SecurityIdentifier]) -ne $owner -or
            $rules.Count -ne 1 -or $rules[0].IdentityReference -ne $owner -or
            $rules[0].AccessControlType -ne [Security.AccessControl.AccessControlType]::Allow -or
            $rules[0].FileSystemRights -ne [Security.AccessControl.FileSystemRights]::FullControl -or
            $rules[0].InheritanceFlags -ne $inheritance -or
            $rules[0].PropagationFlags -ne [Security.AccessControl.PropagationFlags]::None) { throw 'PRIVATE_ACL_VERIFICATION_FAILED' }
}

function Assert-PortsFree {
    if (@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
            Where-Object LocalPort -In @(8080, 8005)).Count) { throw 'LOOPBACK_PORT_IN_USE' }
}

function Save-Json([string]$Path, $Value) {
    $Value | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $Path -Encoding utf8
}

function Find-CandidateProcess($Configuration, $Runtime) {
    $candidate = Get-CimInstance Win32_Process -Filter "ProcessId = $($Runtime.processId)"
    if ($null -eq $candidate) { return $null }
    try {
        $actualCreatedAt = [DateTimeOffset]$candidate.CreationDate
        # ConvertFrom-Json can materialize the retained ISO value as DateTime.
        $expectedCreatedAt = if ($Runtime.createdAt -is [DateTime] -or $Runtime.createdAt -is [DateTimeOffset]) {
            [DateTimeOffset]$Runtime.createdAt
        } else {
            [DateTimeOffset]::Parse([string]$Runtime.createdAt, [Globalization.CultureInfo]::InvariantCulture)
        }
    } catch { throw 'PROCESS_IDENTITY_MISMATCH' }
    if ($actualCreatedAt.UtcTicks -ne $expectedCreatedAt.UtcTicks -or
            -not $candidate.ExecutablePath.Equals((Join-Path $Configuration.javaHome 'bin\java.exe'),
                [StringComparison]::OrdinalIgnoreCase) -or
            -not $candidate.CommandLine.Contains("-Dcatalina.base=$($Configuration.catalinaBase)")) {
        throw 'PROCESS_IDENTITY_MISMATCH'
    }
    return $candidate
}

function Java-Arguments($Configuration, [string]$Command) {
    # Start-Process joins ArgumentList as a Windows command line. Each path-bearing
    # argument is quoted once, without invoking cmd.exe or another command shell.
    return @('--enable-native-access=ALL-UNNAMED',
        ('"-Djava.util.logging.config.file=' + (Join-Path $Configuration.catalinaBase 'conf\logging.properties') + '"'),
        '-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager',
        ('"-Dcatalina.home=' + $Configuration.tomcatHome + '"'),
        ('"-Dcatalina.base=' + $Configuration.catalinaBase + '"'),
        ('"-Djava.io.tmpdir=' + (Join-Path $Configuration.catalinaBase 'temp') + '"'),
        '-Djava.protocol.handler.pkgs=org.apache.catalina.webresources',
        '-classpath', ('"' + (Join-Path $Configuration.tomcatHome 'bin\bootstrap.jar') + ';' +
            (Join-Path $Configuration.tomcatHome 'bin\tomcat-juli.jar') + '"'),
        'org.apache.catalina.startup.Bootstrap', $Command)
}

$baseDirectory = Full-LocalPath $CatalinaBase
$manifestPath = Join-Path $baseDirectory 'candidate.json'
$runtimePath = Join-Path $baseDirectory 'runtime.json'

if ($Action -eq 'Prepare') {
    $storageDirectory = Full-LocalPath $StorageRoot
    Assert-LocalNtfs $baseDirectory
    Assert-LocalNtfs $storageDirectory
    $tomcatDirectory = Full-LocalPath $TomcatHome
    $javaDirectory = Full-LocalPath $JavaHome
    $candidateWar = Full-LocalPath $War
    foreach ($source in @($tomcatDirectory, $javaDirectory, $candidateWar)) { Assert-NoReparse $source }
    foreach ($target in @($baseDirectory, $storageDirectory)) {
        Assert-EmptyTarget $target
        foreach ($source in @($tomcatDirectory, $javaDirectory, (Split-Path -Parent $candidateWar))) {
            if ((Contains-Path $source $target) -or (Contains-Path $target $source)) { throw 'TARGET_SOURCE_OVERLAP' }
        }
    }
    if ((Contains-Path $baseDirectory $storageDirectory) -or (Contains-Path $storageDirectory $baseDirectory)) {
        throw 'STORAGE_BASE_OVERLAP'
    }
    foreach ($required in @('bin\bootstrap.jar', 'bin\tomcat-juli.jar', 'lib\catalina.jar',
            'conf\web.xml', 'conf\catalina.properties', 'conf\logging.properties')) {
        if (-not (Test-Path -LiteralPath (Join-Path $tomcatDirectory $required) -PathType Leaf)) { throw 'TOMCAT_HOME_INCOMPLETE' }
    }
    if (-not (Test-Path -LiteralPath (Join-Path $javaDirectory 'bin\java.exe') -PathType Leaf)) { throw 'JAVA_HOME_INVALID' }
    $javaRelease = Get-Content -Raw -LiteralPath (Join-Path $javaDirectory 'release')
    if ($javaRelease -notmatch '(?m)^JAVA_VERSION="([0-9]+)' -or [int]$Matches[1] -lt 21) { throw 'JAVA_21_REQUIRED' }
    if (-not (Test-Path -LiteralPath $candidateWar -PathType Leaf) -or [IO.Path]::GetExtension($candidateWar) -ne '.war') {
        throw 'CANDIDATE_WAR_REQUIRED'
    }
    $tomcatJar = [IO.Compression.ZipFile]::OpenRead((Join-Path $tomcatDirectory 'lib\catalina.jar'))
    try {
        $entry = $tomcatJar.GetEntry('org/apache/catalina/util/ServerInfo.properties')
        if ($null -eq $entry) { throw 'TOMCAT_HOME_INCOMPLETE' }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $serverInfo = $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally { $tomcatJar.Dispose() }
    if ($serverInfo -notmatch '(?m)^server.info=Apache Tomcat/(10\.1\.[0-9]+)\s*$') { throw 'TOMCAT_10_1_REQUIRED' }
    $tomcatVersion = $Matches[1]
    Assert-PortsFree
    New-PrivateDirectory $baseDirectory
    New-PrivateDirectory $storageDirectory
    foreach ($directory in @('conf\Catalina\localhost', 'logs', 'temp', 'webapps', 'work', 'lib')) {
        [IO.Directory]::CreateDirectory((Join-Path $baseDirectory $directory)) | Out-Null
    }
    foreach ($file in @('web.xml', 'catalina.properties', 'logging.properties')) {
        Copy-Item -LiteralPath (Join-Path $tomcatDirectory "conf\$file") -Destination (Join-Path $baseDirectory "conf\$file")
    }
    '<Context />' | Set-Content -LiteralPath (Join-Path $baseDirectory 'conf\context.xml') -Encoding utf8
    $shutdownToken = 'DND-' + [guid]::NewGuid().ToString('N')
    @"
<?xml version="1.0" encoding="UTF-8"?>
<Server port="8005" address="127.0.0.1" shutdown="$shutdownToken">
  <Service name="Catalina">
    <Connector address="127.0.0.1" port="8080" protocol="HTTP/1.1" connectionTimeout="20000" maxPostSize="16778240" />
    <Engine name="Catalina" defaultHost="localhost">
      <Host name="localhost" appBase="webapps" unpackWARs="true" autoDeploy="false" deployOnStartup="true" />
    </Engine>
  </Service>
</Server>
"@ | Set-Content -LiteralPath (Join-Path $baseDirectory 'conf\server.xml') -Encoding utf8
    $escapedRoot = [Security.SecurityElement]::Escape($storageDirectory)
    "<Context><Parameter name=`"DND_STORAGE_ROOT`" value=`"$escapedRoot`" override=`"false`" /></Context>" |
        Set-Content -LiteralPath (Join-Path $baseDirectory 'conf\Catalina\localhost\ROOT.xml') -Encoding utf8
    Copy-Item -LiteralPath $candidateWar -Destination (Join-Path $baseDirectory 'webapps\ROOT.war')
    $configuration = [ordered]@{ schema = 1; catalinaBase = $baseDirectory; storageRoot = $storageDirectory;
        tomcatHome = $tomcatDirectory; tomcatVersion = $tomcatVersion; javaHome = $javaDirectory;
        candidateWar = $candidateWar; warSha256 = (Get-FileHash -LiteralPath $candidateWar -Algorithm SHA256).Hash;
        warBytes = (Get-Item -LiteralPath $candidateWar).Length }
    Save-Json $manifestPath $configuration
    [pscustomobject]@{ status = 'PREPARED'; catalinaBase = $baseDirectory; storageRoot = $storageDirectory;
        tomcatVersion = $tomcatVersion; warSha256 = $configuration.warSha256 }
    return
}

Assert-NoReparse $baseDirectory
$configuration = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
if ($configuration.schema -ne 1 -or $configuration.catalinaBase -ne $baseDirectory) { throw 'CANDIDATE_IDENTITY_MISMATCH' }
foreach ($requiredPath in @($configuration.tomcatHome, $configuration.javaHome, $configuration.storageRoot)) {
    Assert-NoReparse (Full-LocalPath $requiredPath)
}
$runtime = if (Test-Path -LiteralPath $runtimePath) { Get-Content -Raw -LiteralPath $runtimePath | ConvertFrom-Json } else { $null }
$existing = if ($null -ne $runtime) { Find-CandidateProcess $configuration $runtime } else { $null }
if ($Action -eq 'Status') {
    [pscustomobject]@{ status = $(if ($null -ne $existing) { 'RUNNING' } else { 'STOPPED' });
        processId = $(if ($null -ne $existing) { $existing.ProcessId } else { $null });
        catalinaBase = $baseDirectory; storageRoot = $configuration.storageRoot }
    return
}
if ($Action -eq 'Stop') {
    if ($null -eq $existing) { [pscustomobject]@{ status = 'STOPPED'; catalinaBase = $baseDirectory }; return }
    $runningProcess = [Diagnostics.Process]::GetProcessById($existing.ProcessId)
    $stopper = Start-Process -FilePath (Join-Path $configuration.javaHome 'bin\java.exe') -ArgumentList (Java-Arguments $configuration 'stop') -PassThru -Wait -NoNewWindow
    if ($stopper.ExitCode -ne 0) { throw 'CANDIDATE_STOP_FAILED' }
    if (-not $runningProcess.WaitForExit(30000)) { throw 'CANDIDATE_STOP_TIMEOUT' }
    [pscustomobject]@{ status = 'STOPPED'; processId = $existing.ProcessId; catalinaBase = $baseDirectory }
    return
}
if ($null -ne $existing) { throw 'CANDIDATE_ALREADY_RUNNING' }
Assert-PortsFree
if ((Get-FileHash -LiteralPath (Join-Path $baseDirectory 'webapps\ROOT.war') -Algorithm SHA256).Hash -ne $configuration.warSha256) {
    throw 'CANDIDATE_WAR_MISMATCH'
}
$process = Start-Process -FilePath (Join-Path $configuration.javaHome 'bin\java.exe') -ArgumentList (Java-Arguments $configuration 'start') -PassThru -WorkingDirectory $baseDirectory -RedirectStandardOutput (Join-Path $baseDirectory 'logs\bootstrap.stdout.log') -RedirectStandardError (Join-Path $baseDirectory 'logs\bootstrap.stderr.log')
$nativeProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $($process.Id)"
if ($null -eq $nativeProcess) { throw "CANDIDATE_EXITED processId=$($process.Id); retained logs under the candidate base" }
Save-Json $runtimePath @{ processId = $process.Id; createdAt = $nativeProcess.CreationDate.ToUniversalTime().ToString('o') }
# Cold-start acceptance includes first WAR expansion and annotation scanning.
# This harness bound is not a product response-time target.
$deadline = [DateTime]::UtcNow.AddSeconds(120)
do {
    if ($process.HasExited) { throw "CANDIDATE_EXITED processId=$($process.Id); retained logs under the candidate base" }
    try {
        $response = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/api/host/storage/readiness' -TimeoutSec 2 -SkipHttpErrorCheck
        $readiness = $response.Content | ConvertFrom-Json
        $listeners = @(Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue)
        if ($readiness.mode -eq 'SQLITE' -and $listeners.Count -eq 1 -and
                $listeners[0].OwningProcess -eq $process.Id -and $listeners[0].LocalAddress -eq '127.0.0.1') {
            [pscustomobject]@{ status = 'STARTED'; processId = $process.Id; catalinaBase = $baseDirectory;
                storageRoot = $configuration.storageRoot; httpStatus = $response.StatusCode; readiness = $readiness }
            return
        }
    } catch { }
    Start-Sleep -Milliseconds 250
} while ([DateTime]::UtcNow -lt $deadline)
throw "CANDIDATE_READINESS_TIMEOUT processId=$($process.Id); process and logs retained; use Status and Stop for this base"
