package com.dndtool.service.storage;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/** Filesystem boundary for private POSIX directories and native Windows local NTFS. */
final class ManagedCampaignFiles {
    private ManagedCampaignFiles() { }
    static boolean windows() { return System.getProperty("os.name").startsWith("Windows"); }

    static void ensureDirectory(Path path) throws IOException {
        checkAncestors(path);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) ensureDirectory(parent);
            createDirectory(path);
        }
        directory(path);
    }

    static void createDirectory(Path path) throws IOException {
        checkAncestors(path.getParent());
        Files.createDirectory(path, privateAttribute(path, true));
        privateDirectory(path);
    }

    static FileAttribute<?> privateAttribute(Path path, boolean directory) throws IOException {
        if (!windows()) return PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        UserPrincipal owner = currentUser(path);
        var builder = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if (directory) builder.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT);
        List<AclEntry> acl = List.of(builder.build());
        return new FileAttribute<List<AclEntry>>() {
            public String name() { return "acl:acl"; }
            public List<AclEntry> value() { return acl; }
        };
    }

    static void privateDirectory(Path path) throws IOException {
        directory(path);
        if (windows()) {
            if (path.getRoot().toString().startsWith("\\\\") || !Files.getFileStore(path).type().equalsIgnoreCase("NTFS"))
                throw new IOException("Native Windows managed storage requires a local NTFS drive");
            privateAcl(path, true);
        } else {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
            if (!permissions.equals(PosixFilePermissions.fromString("rwx------"))) throw new IOException("Managed directories require owner-only rwx permissions");
        }
    }

    static void privateAcl(Path path, boolean directory) throws IOException {
        if (!windows()) return;
        UserPrincipal owner = currentUser(path);
        AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null || !view.getOwner().equals(owner)) throw new IOException("Managed entry must be owned by the current Windows account");
        List<AclEntry> acl = view.getAcl();
        if (acl.isEmpty()) throw new IOException("Managed entry has no private Windows ACL");
        boolean full = false;
        for (AclEntry entry : acl) {
            if (entry.type() != AclEntryType.ALLOW || !entry.principal().equals(owner)
                    || entry.flags().contains(AclEntryFlag.INHERIT_ONLY)) throw new IOException("Managed ACL grants access outside the current Windows account");
            if (entry.permissions().containsAll(EnumSet.allOf(AclEntryPermission.class))
                    && (!directory || entry.flags().containsAll(Set.of(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT)))) full = true;
        }
        if (!full) throw new IOException("Managed ACL must grant and inherit full access for its owner");
    }

    private static UserPrincipal currentUser(Path path) throws IOException {
        return path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
    }

    static void checkAncestors(Path path) throws IOException {
        for (Path p = path; p != null; p = p.getParent()) {
            if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) continue;
            BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            // Windows reports junctions and other reparse points as isOther, including directory junctions.
            if (a.isSymbolicLink() || a.isOther() || !a.isDirectory()) throw new IOException("Link, reparse point or non-directory in managed path");
        }
    }

    static void directory(Path path) throws IOException {
        checkAncestors(path);
        BasicFileAttributes a = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!a.isDirectory() || a.isSymbolicLink() || a.isOther()) throw new IOException("Managed directory missing or unsafe");
    }

    static void file(Path path, boolean allowMissing) throws IOException {
        checkAncestors(path.getParent());
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (allowMissing) return;
            throw new IOException("Managed file is missing");
        }
        BasicFileAttributes a = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!a.isRegularFile() || a.isSymbolicLink() || a.isOther()) throw new IOException("Managed file is unsafe");
        privateAcl(path, false);
    }

    static void forceDirectory(Path path) throws IOException {
        directory(path);
        // Windows Java NIO cannot open a directory channel for FlushFileBuffers. Files and SQLite
        // are still flushed, but NTFS namespace persistence across power loss is not promised.
        if (windows()) return;
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) { channel.force(true); }
    }

    /** The exclusive root lock serializes all managed namespace changes; reject existing names. */
    static void moveDirectory(Path source, Path target) throws IOException {
        directory(source); checkAncestors(target.getParent());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(target.toString());
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    }
}
