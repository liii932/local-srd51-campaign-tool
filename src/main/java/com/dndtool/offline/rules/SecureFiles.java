package com.dndtool.offline.rules;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Descriptor-relative, no-follow IO. Unsupported providers fail closed, including native Windows. */
final class SecureFiles {
    private SecureFiles() { }
    static SecureDirectoryStream<Path> directory(Path path) throws IOException {
        Path absolute=path.toAbsolutePath();
        if (!absolute.normalize().equals(absolute)) throw new IOException("Non-canonical local path");
        DirectoryStream<Path> first=Files.newDirectoryStream(absolute.getRoot());
        if (!(first instanceof SecureDirectoryStream<Path> current)) { first.close(); throw new IOException("Secure directory handles required"); }
        try {
            for(Path part:absolute) {
                var next=current.newDirectoryStream(part, LinkOption.NOFOLLOW_LINKS);
                current.close(); current=next;
            }
            return current;
        } catch(Throwable failure) { current.close(); throw failure; }
    }
    static byte[] read(SecureDirectoryStream<Path> root, String name, int limit) throws IOException {
        Path relative=Path.of(name);
        if (relative.getNameCount()>1) {
            try(var child=root.newDirectoryStream(relative.getName(0),LinkOption.NOFOLLOW_LINKS)) {
                return read(child,relative.subpath(1,relative.getNameCount()).toString(),limit);
            }
        }
        var attrs=root.getFileAttributeView(relative,BasicFileAttributeView.class,LinkOption.NOFOLLOW_LINKS).readAttributes();
        if(!attrs.isRegularFile() || attrs.size()>limit) throw new IOException("Not a bounded regular file");
        try(SeekableByteChannel channel=root.newByteChannel(relative,Set.of(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS))) {
            if(channel.size()>limit) throw new IOException("File budget exceeded");
            var out=new ByteArrayOutputStream(Math.min(limit,8192));
            ByteBuffer buffer=ByteBuffer.allocate(4096);
            while(channel.read(buffer)!=-1) {
                if(out.size()+buffer.position()>limit) throw new IOException("File budget exceeded");
                out.write(buffer.array(),0,buffer.position()); buffer.clear();
            }
            BasicFileAttributes after=root.getFileAttributeView(relative,BasicFileAttributeView.class,LinkOption.NOFOLLOW_LINKS).readAttributes();
            if(!after.isRegularFile() || !Objects.equals(attrs.fileKey(),after.fileKey())
                    || attrs.size()!=after.size() || !attrs.lastModifiedTime().equals(after.lastModifiedTime()))
                throw new IOException("File changed while reading");
            return out.toByteArray();
        }
    }
    static byte[] read(Path file,int limit) throws IOException {
        try(var directory=directory(file.toAbsolutePath().getParent())) { return read(directory,file.getFileName().toString(),limit); }
    }
    static Set<String> inventory(SecureDirectoryStream<Path> root) throws IOException {
        Set<String> entries=new TreeSet<>(); inventory(root,"",entries); return entries;
    }
    private static void inventory(SecureDirectoryStream<Path> directory,String prefix,Set<String> entries) throws IOException {
        for(Path path:directory) {
            String name=prefix+path.getFileName();
            if(entries.size()>=7 || !entries.add(name)) throw new IOException("Directory budget exceeded");
            var attr=directory.getFileAttributeView(path.getFileName(),BasicFileAttributeView.class,LinkOption.NOFOLLOW_LINKS).readAttributes();
            if(attr.isDirectory() && name.equals("character")) {
                try(var child=directory.newDirectoryStream(path.getFileName(),LinkOption.NOFOLLOW_LINKS)) { inventory(child,name+"/",entries); }
            } else if(!attr.isRegularFile()) throw new IOException("Unexpected directory or link");
        }
    }
}
