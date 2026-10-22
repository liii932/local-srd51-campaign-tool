package com.dndtool.offline.rules;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

/** One protected maintenance state directory per source lineage, shared by all permitted executors.
 * File and directory force + actual readback precede every database write attempt.
 */
public final class TicketStore implements AutoCloseable {
    private final Path directory;
    private final SecureDirectoryStream<Path> root;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final FileChannel directoryChannel;
    public TicketStore(Path directory) throws IOException {
        this.directory=directory.toAbsolutePath();root=SecureFiles.directory(this.directory);
        FileChannel channel=null,dir=null;FileLock acquired=null;
        try {
            // This directory must be owned and writable only by the controlled maintenance principal.
            var permissions=Files.getPosixFilePermissions(this.directory,LinkOption.NOFOLLOW_LINKS);
            if(permissions.stream().anyMatch(p->p.name().startsWith("GROUP_")||p.name().startsWith("OTHERS_")))
                throw new IOException("Maintenance state directory must be owner-only");
            var raw=root.newByteChannel(Path.of("executor.lock"),Set.of(StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS));
            if(!(raw instanceof FileChannel file)) {raw.close();throw new IOException("Durable file channel required");}
            channel=file;acquired=channel.tryLock();if(acquired==null)throw new IOException("Maintenance executor already active");
            dir=FileChannel.open(this.directory,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS);dir.force(true);
            var anchored=root.getFileAttributeView(java.nio.file.attribute.BasicFileAttributeView.class).readAttributes();
            var named=Files.readAttributes(this.directory,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(anchored.fileKey()==null || !anchored.fileKey().equals(named.fileKey()))throw new IOException("Maintenance directory changed");
            channel.force(true);lockChannel=channel;lock=acquired;directoryChannel=dir;
        } catch(Throwable failure) {
            if(acquired!=null)acquired.close();if(channel!=null)channel.close();if(dir!=null)dir.close();root.close();throw failure;
        }
    }
    public String save(OperationTicket ticket) throws IOException {
        String name=ticket.operationId()+".json";writeNew(name,ticket.encode());
        if(!load(name).equals(ticket))throw new IOException("Ticket readback mismatch");return name;
    }
    public String save(Schema2OperationTicket ticket) throws IOException {
        String name=ticket.operationId()+".json";writeNew(name,ticket.encode());
        if(!loadSchema2(name).equals(ticket))throw new IOException("Ticket readback mismatch");return name;
    }
    void bind(MaintenanceEvidence evidence)throws IOException {
        if(!directory.equals(evidence.stateDirectory()))throw new IOException("Uncontrolled state directory");
        byte[] binding=(evidence.target()+"\n"+evidence.lineage()+"\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        try {if(!Arrays.equals(binding,SecureFiles.read(root,"source",1024)))throw new IOException("State directory belongs to another source lineage");}
        catch(NoSuchFileException absent){writeNew("source",binding);}
    }
    public OperationTicket load(String name) throws IOException {
        if(!name.matches("[0-9a-f-]{36}[.]json"))throw OfflineJson.bad();
        var ticket=OperationTicket.decode(SecureFiles.read(root,name,8192));
        if(!name.equals(ticket.operationId()+".json"))throw OfflineJson.bad();return ticket;
    }
    public Schema2OperationTicket loadSchema2(String name) throws IOException {
        if(!name.matches("[0-9a-f-]{36}[.]json"))throw OfflineJson.bad();
        var ticket=Schema2OperationTicket.decode(SecureFiles.read(root,name,8192));
        if(!name.equals(ticket.operationId()+".json"))throw OfflineJson.bad();return ticket;
    }
    /** Every protocol shares this marker; an unknown or malformed marker also blocks new work. */
    public void requireClear() throws IOException {
        try {SecureFiles.read(root,"pending",8192);}
        catch(NoSuchFileException absent){return;}
        throw new IOException("Unresolved maintenance attempt; explicit resolution required");
    }
    public void begin(OperationTicket ticket) throws IOException {
        requireClear();if(!load(ticket.operationId()+".json").equals(ticket))throw new IOException("Unsaved ticket");
        writeNew("pending",ticket.encode());
        if(!ticket.equals(pending()))throw new IOException("Pending intent readback mismatch");
    }
    public OperationTicket pending() throws IOException {
        try{return OperationTicket.decode(SecureFiles.read(root,"pending",8192));}
        catch(NoSuchFileException absent){return null;}
    }
    public void begin(Schema2OperationTicket ticket) throws IOException {
        requireClear();if(!loadSchema2(ticket.operationId()+".json").equals(ticket))throw new IOException("Unsaved ticket");
        writeNew("pending",ticket.encode());
        if(!ticket.equals(pendingSchema2()))throw new IOException("Pending intent readback mismatch");
    }
    public Schema2OperationTicket pendingSchema2() throws IOException {
        try{return Schema2OperationTicket.decode(SecureFiles.read(root,"pending",8192));}
        catch(NoSuchFileException absent){return null;}
    }
    public void saveReview(Schema2OperationTicket ticket,byte[] review,byte[] zip) throws IOException {
        if(ticket.kind()!=Schema2OperationTicket.Kind.ACCEPT || review.length>Schema2Review.MAX_BYTES
                || zip.length>com.dndtool.module.composition.ProductionRuleArtifact.MAX_ZIP_BYTES
                || !ticket.reviewSha256().equals(RuleArtifact.sha256(review))
                || !ticket.rawZipSha256().equals(RuleArtifact.sha256(zip)))throw new IOException("Review binding mismatch");
        writeNew(ticket.operationId()+".review.txt",review);
        writeNew(ticket.operationId()+".review.zip",zip);
    }
    byte[] review(Schema2OperationTicket ticket) throws IOException {
        return SecureFiles.read(root,ticket.operationId()+".review.txt",Schema2Review.MAX_BYTES);
    }
    byte[] reviewZip(Schema2OperationTicket ticket) throws IOException {
        return SecureFiles.read(root,ticket.operationId()+".review.zip",com.dndtool.module.composition.ProductionRuleArtifact.MAX_ZIP_BYTES);
    }
    public Path reviewPath(Schema2OperationTicket ticket) {return directory.resolve(ticket.operationId()+".review.txt");}
    public void resolved(OperationTicket ticket) throws IOException {
        OperationTicket pending=pending();
        if(pending==null)return;
        if(!pending.equals(ticket))throw new IOException("Different unresolved operation");
        root.deleteFile(Path.of("pending"));directoryChannel.force(true);
    }
    public void resolved(Schema2OperationTicket ticket) throws IOException {
        Schema2OperationTicket pending=pendingSchema2();
        if(pending==null)return;
        if(!pending.equals(ticket))throw new IOException("Different unresolved operation");
        root.deleteFile(Path.of("pending"));directoryChannel.force(true);
    }
    private void writeNew(String name,byte[] bytes)throws IOException {
        try(var raw=root.newByteChannel(Path.of(name),Set.of(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS))) {
            if(!(raw instanceof FileChannel channel))throw new IOException("Durable file channel required");
            ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
        }
        directoryChannel.force(true);
        if(!Arrays.equals(bytes,SecureFiles.read(root,name,Math.max(8192,bytes.length))))throw new IOException("Durable readback mismatch");
    }
    @Override public void close() throws IOException {
        try{lock.release();}finally{try{lockChannel.close();}finally{try{directoryChannel.close();}finally{root.close();}}}
    }
}
