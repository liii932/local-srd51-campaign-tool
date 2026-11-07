package com.dndtool.service.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Native Windows provider checks. Run on the Windows JVM, never infer these from DrvFS. */
@EnabledOnOs(OS.WINDOWS)
class CampaignWindowsFilesystemTest {
    @TempDir Path temporary;

    @Test void uncRootIsRejectedBeforeAnyFilesystemAccess() {
        IOException failure = assertThrows(IOException.class,
                () -> new CampaignStorageService(Path.of("\\\\invalid.test.invalid\\share\\never-created")));
        assertTrue(failure.getMessage().contains("UNC roots are unsupported"));
    }

    @Test void newlyCreatedTreeAndAllLiveFilesHaveOnlyTheCurrentAccountAcl() throws Exception {
        Path root = temporary.resolve("managed");
        try (var service = new CampaignStorageService(root)) {
            CampaignRuleFixtures.create(service, "Windows 权限");
            try (var paths = Files.walk(root)) {
                for (Path path : paths.toList()) {
                    if (Files.isDirectory(path)) ManagedCampaignFiles.privateDirectory(path);
                    else ManagedCampaignFiles.privateAcl(path, false);
                }
            }
        }
    }

    @Test void broadExistingRootAclIsRejectedWithoutRepair() throws Exception {
        Path root = temporary.resolve("broad"); ManagedCampaignFiles.createDirectory(root);
        AclFileAttributeView view = Files.getFileAttributeView(root, AclFileAttributeView.class);
        List<AclEntry> entries = new ArrayList<>(view.getAcl());
        UserPrincipal everyone = root.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("Everyone");
        entries.add(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(everyone).setPermissions(AclEntryPermission.READ_DATA).build());
        view.setAcl(entries); List<AclEntry> before = view.getAcl();
        assertThrows(IOException.class, () -> new CampaignStorageService(root)); assertEquals(before, view.getAcl());
    }

    @Test void junctionInRootAncestorIsRejectedWithoutTouchingItsTarget() throws Exception {
        Path target = temporary.resolve("outside"), junction = temporary.resolve("junction"); Files.createDirectory(target);
        Files.writeString(target.resolve("keep.txt"), "untouched");
        Process process = new ProcessBuilder(System.getenv("SystemRoot") + "\\System32\\cmd.exe", "/c", "mklink", "/J", junction.toString(), target.toString()).redirectErrorStream(true).start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS)); assertEquals(0, process.exitValue(), new String(process.getInputStream().readAllBytes()));
        try { assertThrows(IOException.class, () -> new CampaignStorageService(junction.resolve("managed"))); }
        finally { Files.delete(junction); }
        assertEquals("untouched", Files.readString(target.resolve("keep.txt"))); assertFalse(Files.exists(target.resolve("managed")));
    }

    @Test void widenedResidualDirectoryAclIsRejectedBeforeDatabaseAdoption() throws Exception {
        Path root = temporary.resolve("managed"); String run;
        try (var service = new CampaignStorageService(root)) { CampaignRuleFixtures.create(service, "私有运行"); run = service.status().runId(); }
        Path directory = root.resolve("runs").resolve(run);
        var view = Files.getFileAttributeView(directory, AclFileAttributeView.class); var entries = new ArrayList<>(view.getAcl());
        UserPrincipal everyone = root.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("Everyone");
        entries.add(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(everyone).setPermissions(AclEntryPermission.WRITE_DATA).build());
        view.setAcl(entries);
        try (var service = new CampaignStorageService(root)) {
            assertEquals("STORAGE_IO", assertThrows(StorageException.class, () -> service.recover(run)).code());
            assertEquals(entries, view.getAcl()); assertTrue(Files.exists(directory.resolve("run.db")));
        }
    }

    @Test void independentProcessRunLockPreventsRecoveryAndReleasesAtExit() throws Exception {
        Path root = temporary.resolve("managed"); String run;
        try (var service = new CampaignStorageService(root)) { CampaignRuleFixtures.create(service, "锁"); run = service.status().runId(); }
        Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", classpath(),
                RunLockChild.class.getName(), root.resolve("runs").resolve(run).resolve("run.lock").toString()).redirectErrorStream(true).start();
        try {
            assertEquals("LOCKED", new java.io.BufferedReader(new java.io.InputStreamReader(child.getInputStream())).readLine());
            try (var service = new CampaignStorageService(root)) { assertEquals("RUN_LOCKED", assertThrows(StorageException.class, () -> service.recover(run)).code()); }
        } finally { child.destroyForcibly(); assertTrue(child.waitFor(10, TimeUnit.SECONDS)); }
        try (var service = new CampaignStorageService(root)) { assertEquals("锁", service.recover(run).name()); }
    }


    /** Disposable native acceptance runner using the same test bodies and assertions as Surefire. */
    public static final class NativeAcceptance {
        public static void main(String[] args) throws Exception {
            if (!ManagedCampaignFiles.windows()) throw new IllegalStateException("A native Windows JVM is required");
            Path base = Path.of(args[0]); Files.createDirectory(base);
            int passed = 0, skipped = 0;
            Set<String> symlinkTests = Set.of("retryAndExactDeletionRetainPartialForeignAndLinkedStaging", "managedFileLinksCannotBeImportedOverOrDeleted", "residualLinksAndAnotherRunLockPreventAbandonment");
            for (Class<?> type : List.of(CampaignWindowsFilesystemTest.class, CampaignPreparationTest.class, CampaignStorageCrashTest.class,
                    CampaignStorageServiceTest.class, CampaignStorageLifecycleTest.class, CampaignDomainRecoveryTest.class)) {
                for (var method : type.getDeclaredMethods()) {
                    if (!method.isAnnotationPresent(Test.class) && !method.isAnnotationPresent(org.junit.jupiter.params.ParameterizedTest.class)) continue;
                    if (symlinkTests.contains(method.getName())) { System.out.println("SKIP symlink privilege dependent: " + method.getName()); skipped++; continue; }
                    List<Object[]> cases = new ArrayList<>();
                    var source = method.getAnnotation(org.junit.jupiter.params.provider.EnumSource.class);
                    if (source == null) cases.add(new Object[0]);
                    else for (String name : source.names()) cases.add(new Object[] { CampaignStorageService.Boundary.valueOf(name) });
                    for (Object[] parameters : cases) {
                        Object test = type.getDeclaredConstructor().newInstance();
                        Path temporary = Files.createDirectory(base.resolve("case-" + passed));
                        var field = type.getDeclaredField("temporary"); field.setAccessible(true); field.set(test, temporary);
                        try { method.invoke(test, parameters); }
                        catch (java.lang.reflect.InvocationTargetException e) { throw new AssertionError(type.getSimpleName() + "." + method.getName() + Arrays.toString(parameters), e.getCause()); }
                        passed++; System.out.println("PASS " + type.getSimpleName() + "." + method.getName() + Arrays.toString(parameters));
                    }
                }
            }
            System.out.println("Native Windows storage: passed=" + passed + " skipped=" + skipped);
        }
    }

    public static final class RunLockChild {
        public static void main(String[] args) throws Exception {
            try (FileChannel channel = FileChannel.open(Path.of(args[0]), StandardOpenOption.WRITE); FileLock ignored = channel.lock()) {
                System.out.println("LOCKED"); System.out.flush(); Thread.sleep(60_000);
            }
        }
    }
    private static String classpath() { return System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")); }
}
