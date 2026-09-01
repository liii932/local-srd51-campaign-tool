[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9._-]{1,64}$')]
    [string] $DatabaseUser,

    [Parameter(Mandatory = $true)]
    [string] $BackupDirectory,

    [Parameter(Mandatory = $true)]
    [string] $MySqlDumpPath,

    [ValidateRange(1, 65535)]
    [int] $DatabasePort = 3306
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$databaseName = 'dnd_tool_se'
$databaseHost = '127.0.0.1'
$netstatPath = 'C:\Windows\System32\netstat.exe'
$repositoryRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot '..'))
$resolvedBackupDirectory = [System.IO.Path]::GetFullPath($BackupDirectory)
$resolvedDumpPath = [System.IO.Path]::GetFullPath($MySqlDumpPath)
$repositoryPrefix = $repositoryRoot.TrimEnd(
    [System.IO.Path]::DirectorySeparatorChar,
    [System.IO.Path]::AltDirectorySeparatorChar) +
    [System.IO.Path]::DirectorySeparatorChar

if (-not (Test-Path -LiteralPath $resolvedDumpPath -PathType Leaf)) {
    throw "mysqldump was not found at the approved path: $resolvedDumpPath"
}
if (-not (Test-Path -LiteralPath $netstatPath -PathType Leaf)) {
    throw "netstat was not found at the expected system path: $netstatPath"
}
if ($resolvedBackupDirectory.Equals(
        $repositoryRoot, [System.StringComparison]::OrdinalIgnoreCase) -or
    $resolvedBackupDirectory.StartsWith(
        $repositoryPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Database backups must be written outside the Git repository.'
}

$tcpLines = @(& $netstatPath -ano -p tcp)
$tomcatListeners = @($tcpLines | Select-String -Pattern (
        '^\s*TCP\s+\S+:(?:8005|8080|8443)\s+\S+\s+LISTENING\s+\d+\s*$'))
if ($tomcatListeners.Count -ne 0) {
    throw 'Tomcat must be stopped before creating the acceptance backup.'
}
$mysqlListeners = @($tcpLines | Select-String -Pattern (
        "^\s*TCP\s+\S+:$DatabasePort\s+\S+\s+LISTENING\s+\d+\s*`$"))
if ($mysqlListeners.Count -eq 0) {
    throw "MySQL is not listening on TCP port $DatabasePort."
}

New-Item -ItemType Directory -Path $resolvedBackupDirectory -Force | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$finalPath = Join-Path $resolvedBackupDirectory (
    "dnd-tool-se-db-$timestamp.sql")
$partialPath = "$finalPath.partial"
if ((Test-Path -LiteralPath $finalPath) -or
    (Test-Path -LiteralPath $partialPath)) {
    throw "The timestamped backup target already exists: $finalPath"
}

$arguments = @(
    "--host=$databaseHost",
    "--port=$DatabasePort",
    '--protocol=TCP',
    "--user=$DatabaseUser",
    '--password',
    '--default-character-set=utf8mb4',
    '--single-transaction',
    '--quick',
    '--skip-lock-tables',
    '--triggers',
    '--hex-blob',
    '--set-gtid-purged=OFF',
    '--column-statistics=0',
    '--no-tablespaces',
    '--databases',
    $databaseName,
    "--result-file=$partialPath"
)

try {
    Write-Host 'mysqldump will prompt for the database backup account password.'
    Write-Host 'The password is not stored in this script or placed in process arguments.'

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $resolvedDumpPath
    $startInfo.UseShellExecute = $false
    foreach ($argument in $arguments) {
        [void] $startInfo.ArgumentList.Add($argument)
    }

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        throw 'mysqldump did not start.'
    }
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        throw "mysqldump failed with exit code $($process.ExitCode)."
    }
    if (-not (Test-Path -LiteralPath $partialPath -PathType Leaf)) {
        throw 'mysqldump returned success but did not create the output file.'
    }

    $partialItem = Get-Item -LiteralPath $partialPath
    if ($partialItem.Length -lt 1024) {
        throw "The dump is unexpectedly small: $($partialItem.Length) bytes."
    }

    $requiredMarkers = [ordered]@{
        campaign = $false
        module_release = $false
        game_event = $false
        field_change = $false
        host_operation = $false
        schema_meta_data = $false
    }
    $triggerDefinitionCount = 0
    $reader = [System.IO.StreamReader]::new(
        $partialPath,
        [System.Text.UTF8Encoding]::new($false, $true),
        $true,
        65536)
    try {
        while (($line = $reader.ReadLine()) -ne $null) {
            foreach ($tableName in @(
                    'campaign',
                    'module_release',
                    'game_event',
                    'field_change',
                    'host_operation')) {
                if ($line.Contains("CREATE TABLE ``$tableName``")) {
                    $requiredMarkers[$tableName] = $true
                }
            }
            if ($line.Contains('INSERT INTO `schema_meta`')) {
                $requiredMarkers['schema_meta_data'] = $true
            }
            if ($line -match '(?i)CREATE.*\bTRIGGER\b') {
                $triggerDefinitionCount++
            }
        }
    }
    finally {
        $reader.Dispose()
    }

    $missingMarkers = @($requiredMarkers.GetEnumerator() |
            Where-Object { -not $_.Value } |
            ForEach-Object { $_.Key })
    if ($missingMarkers.Count -ne 0) {
        throw "The dump is missing required markers: $($missingMarkers -join ', ')."
    }
    if ($triggerDefinitionCount -lt 69) {
        throw "The dump contains only $triggerDefinitionCount trigger definitions; at least 69 are required."
    }

    Move-Item -LiteralPath $partialPath -Destination $finalPath
    $finalItem = Get-Item -LiteralPath $finalPath
    $sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $finalPath).Hash

    Write-Output "BACKUP_PATH=$finalPath"
    Write-Output "BACKUP_SIZE=$($finalItem.Length)"
    Write-Output "BACKUP_SHA256=$sha256"
    Write-Output "TRIGGER_DEFINITIONS=$triggerDefinitionCount"
    Write-Output 'BACKUP_VALIDATION=PASS'
    Write-Warning 'This backup contains private campaign data. Keep it outside Git and restrict access.'
}
finally {
    if (Test-Path -LiteralPath $partialPath) {
        Remove-Item -LiteralPath $partialPath -Force
    }
}
