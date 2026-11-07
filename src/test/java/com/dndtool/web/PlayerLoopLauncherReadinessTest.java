package com.dndtool.web;

import com.dndtool.service.storage.CampaignStorageService;
import com.dndtool.service.storage.CampaignRuleFixtures;
import jakarta.servlet.ServletContext;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Pre-acceptance checks only: no HTTP connector, certificate process or browser is started. */
class PlayerLoopLauncherReadinessTest {
    @TempDir Path temporary;

    @Test void relativeAndGitOrDeploymentMarkedRootsFailBeforeCreatingStorage() throws Exception {
        assertEquals("ISOLATED_STORAGE_ROOT_INVALID",assertThrows(PlayerLoopLauncher.StartupFailure.class,
                ()->PlayerLoopLauncher.start(Path.of("relative-private-root"))).code());
        for(String marker:List.of(".git","WEB-INF")) {
            Path directory=Files.createDirectory(temporary.resolve(marker.equals(".git")?"repository":"deployed"));
            Files.createDirectory(directory.resolve(marker));Path missing=directory.resolve("private-root");
            var failure=assertThrows(PlayerLoopLauncher.StartupFailure.class,()->PlayerLoopLauncher.start(missing));
            assertEquals("ISOLATED_STORAGE_LOCATION_REJECTED",failure.code());assertFalse(Files.exists(missing));
            assertFalse(failure.getMessage().contains(directory.toString()));assertNull(failure.getCause());
        }
    }

    @Test void missingRootIsSupportedAndHealthyServiceIsCheckedWithoutCreatingBusinessState() throws Exception {
        // The same production ancestor checks apply: run in the authorized environment without
        // sandbox-injected Git/deployment markers, rather than weakening those checks for tests.
        Path root=temporary.resolve("new-private-store");assertFalse(Files.exists(root));
        PlayerLoopLauncher.validateStorageRoot(root);assertFalse(Files.exists(root));
        try(CampaignStorageService storage=new CampaignStorageService(root)) {
            PlayerLoopLauncher.requireStorageReady(context(storage,null));
            assertEquals("IDLE",storage.status().state());assertEquals(0,storage.status().epoch());
            assertTrue(Files.isDirectory(root.resolve("runs")));assertTrue(Files.isDirectory(root.resolve("saves")));
        }
        assertTrue(Files.isDirectory(root));
    }

    @Test void absentOrClosedServiceCannotBeMistakenForWebApplicationReadiness() throws Exception {
        String privateDetail="private-certificate-password-and-filesystem-path";
        var missing=assertThrows(PlayerLoopLauncher.StartupFailure.class,
                ()->PlayerLoopLauncher.requireStorageReady(context(null,privateDetail)));
        assertEquals("ISOLATED_STORAGE_UNAVAILABLE",missing.code());assertFalse(missing.getMessage().contains(privateDetail));assertNull(missing.getCause());
        CampaignStorageService storage=new CampaignStorageService(temporary.resolve("closed-store"));storage.close();
        assertEquals("ISOLATED_STORAGE_UNAVAILABLE",assertThrows(PlayerLoopLauncher.StartupFailure.class,
                ()->PlayerLoopLauncher.requireStorageReady(context(storage,null))).code());
    }

    @Test void reopenedResidualStoreKeepsItsExplicitHostRecoveryPathWithoutAdvancingState() throws Exception {
        Path root=temporary.resolve("residual-store");String runId;
        try(CampaignStorageService storage=new CampaignStorageService(root)) {
            CampaignRuleFixtures.create(storage, "需要主持恢复");runId=storage.status().runId();
        }
        try(CampaignStorageService reopened=new CampaignStorageService(root)) {
            var before=reopened.status();assertEquals("RESIDUAL",before.state());assertEquals(List.of(runId),before.residualRunIds());
            PlayerLoopLauncher.requireStorageReady(context(reopened,null));
            assertEquals(before,reopened.status());
        }
    }

    @Test void requiredClasspathResourcesMustBePresentNonemptyAndReadable() {
        PlayerLoopLauncher.requireRuntimeResources(CampaignStorageService.class::getResourceAsStream);
        assertEquals("ISOLATED_RUNTIME_RESOURCE_UNAVAILABLE",assertThrows(PlayerLoopLauncher.StartupFailure.class,
                ()->PlayerLoopLauncher.requireRuntimeResources(name->null)).code());
        assertEquals("ISOLATED_RUNTIME_RESOURCE_UNAVAILABLE",assertThrows(PlayerLoopLauncher.StartupFailure.class,
                ()->PlayerLoopLauncher.requireRuntimeResources(name->new ByteArrayInputStream(new byte[0]))).code());
        String privateDetail="private-keystore-password-and-local-path";
        var unreadable=assertThrows(PlayerLoopLauncher.StartupFailure.class,
                ()->PlayerLoopLauncher.requireRuntimeResources(name->{throw new IOException(privateDetail);}));
        assertEquals("ISOLATED_RUNTIME_RESOURCE_UNAVAILABLE",unreadable.code());assertFalse(unreadable.getMessage().contains(privateDetail));assertNull(unreadable.getCause());
        var denied=assertThrows(PlayerLoopLauncher.StartupFailure.class,
                ()->PlayerLoopLauncher.requireRuntimeResources(name->{throw new SecurityException(privateDetail);}));
        assertEquals("ISOLATED_RUNTIME_RESOURCE_UNAVAILABLE",denied.code());assertFalse(denied.getMessage().contains(privateDetail));assertNull(denied.getCause());
    }

    @Test void failureCleanupAttemptsBothContainerPhasesAndDeletesOnlyItsTemporaryCertificateDirectory() throws Exception {
        Path suppliedRoot=Files.createDirectory(temporary.resolve("supplied-store"));Path retained=Files.writeString(suppliedRoot.resolve("retained-save.json"),"retain supplied files");
        Path createdBase=Files.createDirectory(temporary.resolve("launcher-created"));Files.writeString(createdBase.resolve("test-server.p12"),"temporary certificate fixture");
        int[] phases={0,0};String privateDetail="private-certificate-password-and-local-path";
        Server started=(Server)Proxy.newProxyInstance(Server.class.getClassLoader(),new Class[]{Server.class},(proxy,method,args)->{
            if(method.getName().equals("getState"))return LifecycleState.STARTED;
            throw new AssertionError("Unexpected container call: "+method.getName());
        });
        Tomcat failedContainer=new Tomcat() {
            @Override public Server getServer(){return started;}
            @Override public void stop() throws LifecycleException {phases[0]++;throw new LifecycleException(privateDetail);}
            @Override public void destroy() throws LifecycleException {phases[1]++;throw new LifecycleException(privateDetail);}
        };
        var failure=assertThrows(PlayerLoopLauncher.StartupFailure.class,()->PlayerLoopLauncher.cleanupCreatedResources(failedContainer,createdBase));
        assertArrayEquals(new int[]{1,1},phases);assertFalse(Files.exists(createdBase));assertEquals("retain supplied files",Files.readString(retained));
        assertEquals("ISOLATED_CLEANUP_INCOMPLETE",failure.code());assertFalse(failure.getMessage().contains(privateDetail));assertNull(failure.getCause());
    }

    private static ServletContext context(CampaignStorageService storage,Object error) {
        return (ServletContext)Proxy.newProxyInstance(ServletContext.class.getClassLoader(),new Class[]{ServletContext.class},(proxy,method,args)->{
            if(!method.getName().equals("getAttribute"))throw new AssertionError("Unexpected context call: "+method.getName());
            return switch((String)args[0]) {
                case StorageApplicationListener.ENABLED_ATTRIBUTE -> true;
                case StorageApplicationListener.SERVICE_ATTRIBUTE -> storage;
                case StorageApplicationListener.ERROR_ATTRIBUTE -> error;
                default -> throw new AssertionError("Unexpected context attribute");
            };
        });
    }
}
