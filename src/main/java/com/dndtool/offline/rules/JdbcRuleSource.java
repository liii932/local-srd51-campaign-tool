package com.dndtool.offline.rules;

import com.dndtool.module.LanguagePartition;
import com.dndtool.module.ToolPartition;
import com.dndtool.persistence.RuleSchemaMigrations;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.Normalizer;
import java.util.*;

/** Narrow JDBC adapter. Its caller owns the one physical connection and transaction. */
final class JdbcRuleSource {
    static final int BUDGET=16384;
    private final Connection connection;
    JdbcRuleSource(Connection connection) {this.connection=connection;}
    void preflight(MaintenanceEvidence evidence)throws SQLException {
        evidence.fresh();
        var environment=rows("SELECT CAST(DATABASE() AS BINARY), @@server_uuid, CURRENT_USER(), CURRENT_ROLE(), CAST(@@read_only AS SIGNED), CAST(@@super_read_only AS SIGNED), @@session.sql_mode",2);
        if(environment.size()!=1)throw bad();
        Object[] row=environment.getFirst();
        if(!binary(row[0]).equals(RuleSchemaMigrations.DEFAULT_SCHEMA) || !string(row[1]).equals(evidence.text("server_uuid"))
                || !string(row[2]).equals(evidence.text("current_user")) || !string(row[3]).equals("NONE")
                || integer(row[4])!=0 || integer(row[5])!=0)throw bad();
        Set<String> modes=Set.of(string(row[6]).split(","));
        if(!modes.contains("STRICT_ALL_TABLES") && !modes.contains("STRICT_TRANS_TABLES"))throw bad();
        var ledger=rows("SELECT schema_version, script_name, script_sha256 FROM rule_schema_meta ORDER BY schema_version",RuleSchemaMigrations.expectations().size()+1);
        if(ledger.size()!=RuleSchemaMigrations.expectations().size())throw bad();
        for(int i=0;i<ledger.size();i++) {
            var expected=RuleSchemaMigrations.expectations().get(i);var actual=ledger.get(i);
            if(integer(actual[0])!=expected.version() || !binary(actual[1]).equals(expected.scriptName()) || !binary(actual[2]).equals(expected.scriptSha256()))throw bad();
        }
        // Narrow accounts cannot see all trigger definitions or foreign incoming references.
        // Their complete definition/privilege audit is an independent, protected maintenance input.
        var grants=rows("SHOW GRANTS",129);if(grants.size()>128)throw bad();
        List<String> lines=new ArrayList<>();for(var grant:grants) {String line=string(grant[0]);if(line.length()>4096)throw bad();lines.add(line);}
        Collections.sort(lines);
        String digest=RuleArtifact.sha256((String.join("\n",lines)+"\n").getBytes(StandardCharsets.UTF_8));
        if(!digest.equals(evidence.text("grants_sha256")))throw bad();
    }
    Control lock()throws SQLException {
        var rows=rows("SELECT control_id, protocol_version, metadata_row_count, row_version FROM rule_installation_control WHERE control_id = 1 FOR UPDATE",2);
        if(rows.size()!=1)throw bad();return control(rows.getFirst());
    }
    void lockRoot(long id)throws SQLException {
        var rows=rows("SELECT id FROM rule_release WHERE id = ? FOR UPDATE",2,id);
        if(rows.size()!=1 || integer(rows.getFirst()[0])!=id)throw bad();
    }
    State read(Control locked,long minimumVersion)throws SQLException {return read(locked,minimumVersion,false);}
    State read(Control locked,long minimumVersion,boolean lockTarget)throws SQLException {
        var controls=rows("SELECT control_id, protocol_version, metadata_row_count, row_version FROM rule_installation_control ORDER BY control_id",2);
        if(controls.size()!=1 || !locked.equals(control(controls.getFirst())) || locked.version()<minimumVersion)throw bad();
        Map<Long,Root> roots=new LinkedHashMap<>();Set<String> identities=new HashSet<>();
        for(Object[] r:bounded("SELECT id, module_key, release_version, canonical_format_version, archive_format_version, hash_algorithm, content_sha256, release_status, installation_revision, created_at, released_at FROM rule_release ORDER BY id")) {
            var root=new Root(positive(r[0]),binary(r[1]),binary(r[2]),positiveInt(r[3]),positiveInt(r[4]),binary(r[5]),nullableBinary(r[6]),binary(r[7]),integer(r[8]),timestamp(r[9]),nullableTimestamp(r[10]));
            if(!root.key().matches("[a-z][a-z0-9_]*(?:[.][a-z][a-z0-9_]*)*") || root.key().length()>128
                    || !root.release().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}") || !root.algorithm().equals("SHA-256")
                    || root.revision()<0 || root.revision()>BUDGET || !identities.add(root.key()+"/"+root.release())
                    || roots.put(root.id(),root)!=null)throw bad();
            if(root.digest()!=null)RuleArtifact.digestText(root.digest());
            if(root.status().equals("DRAFT")) {if(root.releasedAt()!=null)throw bad();}
            else if(!root.status().equals("RELEASED") || root.releasedAt()==null || root.digest()==null)throw bad();
            if(root.revision()==0 && (!root.status().equals("DRAFT") || root.digest()!=null))throw bad();
        }
        if(lockTarget)for(Root root:roots.values())if(root.isTarget())lockRoot(root.id());
        Map<Revision,Fact> facts=new LinkedHashMap<>();Map<UUID,Fact> operations=new HashMap<>();Map<Long,Long> revisions=new HashMap<>();
        for(Object[] r:bounded("SELECT release_id, installation_revision, source_operation_id, operation_fingerprint_version, operation_digest_sha256, author_schema_version, installation_manifest_version, installation_manifest_sha256, package_display_name, verification_scope, observed_content_sha256, installed_at FROM rule_package_installation ORDER BY release_id, installation_revision")) {
            long id=positive(r[0]),revision=positive(r[1]);byte[] bytes=bytes(r[2]);if(bytes.length!=16)throw bad();
            ByteBuffer buffer=ByteBuffer.wrap(bytes);UUID operation=new UUID(buffer.getLong(),buffer.getLong());
            if(operation.version()!=4 || operation.variant()!=2 || integer(r[3])!=1)throw bad();
            var fact=new Fact(id,revision,operation,binary(r[4]),positiveInt(r[5]),positiveInt(r[6]),binary(r[7]),string(r[8]),binary(r[9]),nullableBinary(r[10]),timestamp(r[11]));
            if(fact.authorVersion()!=1 || fact.manifestVersion()!=1)throw bad();
            RuleArtifact.digestText(fact.fingerprint());RuleArtifact.digestText(fact.manifest());text(fact.name(),120);
            if(fact.scope().equals("PARTITION")) {if(fact.observed()!=null)throw bad();}
            else if(fact.scope().equals("COMPLETE"))RuleArtifact.digestText(fact.observed());else throw bad();
            if(!roots.containsKey(id) || revision!=revisions.getOrDefault(id,0L)+1 || facts.put(new Revision(id,revision),fact)!=null || operations.put(operation,fact)!=null)throw bad();
            revisions.put(id,revision);
        }
        Set<Partition> partitions=new LinkedHashSet<>();
        for(Object[] r:bounded("SELECT release_id, installation_revision, partition_key FROM rule_package_installation_partition ORDER BY release_id, installation_revision, partition_key")) {
            var key=new Revision(positive(r[0]),positive(r[1]));
            String partition=binary(r[2]);
            if(!Set.of(LanguagePartition.KEY,ToolPartition.KEY).contains(partition) || !facts.containsKey(key)
                    || !partitions.add(new Partition(key,partition)))throw bad();
        }
        for(Revision revision:facts.keySet())if(!partitions.contains(new Partition(revision,LanguagePartition.KEY)))throw bad();
        if(locked.count()!=1L+roots.size()+facts.size()+partitions.size() || locked.version()!=facts.size())throw bad();
        for(Root root:roots.values()) {
            if(root.revision()!=revisions.getOrDefault(root.id(),0L))throw bad();
            if(root.revision()>0) {
                Fact current=facts.get(new Revision(root.id(),root.revision()));
                if(!Objects.equals(current.observed(),root.digest()) || root.status().equals("RELEASED")&&!current.scope().equals("COMPLETE"))throw bad();
            }
        }
        // Only one nonempty domain/profile is implemented. Unknown nonempty roots fail closed.
        for(Root root:roots.values())if(root.revision()>0 && !root.isTarget())throw bad();
        var languageRows=rows("SELECT release_id, language_key, display_name, description, category, source_page, sort_order FROM rule_language ORDER BY release_id, language_key",19);
        if(languageRows.size()>18)throw bad();
        Map<Long,List<LanguagePartition.Language>> languages=new HashMap<>();
        for(Object[] r:languageRows) {
            long id=positive(r[0]);Root root=roots.get(id);if(root==null||root.revision()==0||!root.isTarget())throw bad();
            var language=new LanguagePartition.Language(binary(r[1]),string(r[2]),string(r[3]),LanguagePartition.Category.valueOf(binary(r[4])),positiveInt(r[5]),positiveInt(r[6]));
            languages.computeIfAbsent(id,ignored->new ArrayList<>()).add(language);
        }
        Map<Long,LanguagePartition> parsed=new HashMap<>();
        for(Root root:roots.values()) {
            if(root.revision()>0)parsed.put(root.id(),new LanguagePartition(languages.getOrDefault(root.id(),List.of())));
            else if(languages.containsKey(root.id()))throw bad();
        }
        var toolRows=rows("SELECT release_id, tool_key, display_name, description, category, source_page, sort_order FROM rule_tool ORDER BY release_id, tool_key",38);
        if(toolRows.size()>37)throw bad();
        Map<Long,List<ToolPartition.Tool>> tools=new HashMap<>();
        for(Object[] r:toolRows) {
            long id=positive(r[0]);Root root=roots.get(id);
            if(root==null || !root.isTarget() || !partitions.contains(new Partition(new Revision(id,root.revision()),ToolPartition.KEY)))throw bad();
            tools.computeIfAbsent(id,ignored->new ArrayList<>()).add(new ToolPartition.Tool(binary(r[1]),string(r[2]),string(r[3]),
                    ToolPartition.Category.valueOf(binary(r[4])),positiveInt(r[5]),positiveInt(r[6])));
        }
        Map<Long,ToolPartition> parsedTools=new HashMap<>();
        for(Root root:roots.values())if(partitions.contains(new Partition(new Revision(root.id(),root.revision()),ToolPartition.KEY)))
            parsedTools.put(root.id(),new ToolPartition(tools.getOrDefault(root.id(),List.of())));
        return new State(locked,Map.copyOf(roots),Map.copyOf(facts),Map.copyOf(operations),Set.copyOf(partitions),Map.copyOf(parsed),Map.copyOf(parsedTools));
    }
    long expectedRevision()throws SQLException {
        var result=rows("SELECT installation_revision, release_status, canonical_format_version, archive_format_version, hash_algorithm FROM rule_release WHERE module_key = ? AND release_version = ?",2,ascii("dnd5e2014_srd51_se"),ascii("1"));
        if(result.isEmpty())return 0;
        if(result.size()!=1)throw bad();var r=result.getFirst();long revision=integer(r[0]);
        if(revision<0 || revision==Long.MAX_VALUE || !binary(r[1]).equals("DRAFT") || integer(r[2])!=2 || integer(r[3])!=2 || !binary(r[4]).equals("SHA-256"))throw bad();
        return revision;
    }
    Installed install(State before,OperationTicket ticket,RuleArtifact artifact)throws SQLException {
        Root root=before.target();long oldRevision=root==null?0:root.revision();
        if(oldRevision!=ticket.expectedRevision() || oldRevision==Long.MAX_VALUE || before.control().version()==Long.MAX_VALUE)throw bad();
        if(root!=null && (!root.status().equals("DRAFT") || root.canonical()!=2 || root.archive()!=2 || !root.algorithm().equals("SHA-256")))throw bad();
        // Current COMPLETE belongs to a future all-domain profile, not this writer's adoption gate.
        if(root!=null && root.revision()>0 && !before.facts().get(new Revision(root.id(),root.revision())).scope().equals("PARTITION"))throw bad();
        long count=Math.addExact(before.control().count(),root==null?4:3);if(count>BUDGET)throw bad();
        long id;
        if(root==null) {
            execute("INSERT INTO rule_release (module_key, release_version, canonical_format_version, archive_format_version, hash_algorithm) VALUES (?, ?, ?, ?, ?)",ascii("dnd5e2014_srd51_se"),ascii("1"),2,2,ascii("SHA-256"));
            var created=rows("SELECT id, installation_revision, content_sha256, release_status, canonical_format_version, archive_format_version, hash_algorithm FROM rule_release WHERE module_key = ? AND release_version = ?",2,ascii("dnd5e2014_srd51_se"),ascii("1"));
            if(created.size()!=1)throw bad();var row=created.getFirst();id=positive(row[0]);
            if(id==Long.MAX_VALUE || integer(row[1])!=0 || row[2]!=null || !binary(row[3]).equals("DRAFT") || integer(row[4])!=2 || integer(row[5])!=2 || !binary(row[6]).equals("SHA-256"))throw bad();
        } else {
            id=root.id();
            if(oldRevision>0)for(var language:before.languages().get(id).languages())
                execute("DELETE FROM rule_language WHERE release_id = ? AND language_key = ?",id,ascii(language.languageKey()));
        }
        for(var language:artifact.author().languages().languages())execute("INSERT INTO rule_language (release_id, language_key, display_name, description, category, source_page, sort_order) VALUES (?, ?, ?, ?, ?, ?, ?)",id,ascii(language.languageKey()),language.displayName(),language.description(),ascii(language.category().name()),language.sourcePage(),language.sortOrder());
        if(before.tools().containsKey(id))for(var tool:before.tools().get(id).tools())
            execute("DELETE FROM rule_tool WHERE release_id = ? AND tool_key = ?",id,ascii(tool.toolKey()));
        for(var tool:artifact.author().tools().tools())execute("INSERT INTO rule_tool (release_id, tool_key, display_name, description, category, source_page, sort_order) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,ascii(tool.toolKey()),tool.displayName(),tool.description(),ascii(tool.category().name()),tool.sourcePage(),tool.sortOrder());
        execute("UPDATE rule_release SET canonical_format_version = ?, archive_format_version = ?, hash_algorithm = ?, content_sha256 = NULL, installation_revision = ? WHERE id = ? AND module_key = ? AND release_version = ? AND installation_revision = ? AND release_status = ? AND canonical_format_version = ? AND archive_format_version = ? AND hash_algorithm = ? AND content_sha256 <=> ?",2,2,ascii("SHA-256"),oldRevision+1,id,ascii("dnd5e2014_srd51_se"),ascii("1"),oldRevision,ascii("DRAFT"),2,2,ascii("SHA-256"),root==null?null:nullableAscii(root.digest()));
        execute("INSERT INTO rule_package_installation (release_id, installation_revision, source_operation_id, operation_fingerprint_version, operation_digest_sha256, author_schema_version, installation_manifest_version, installation_manifest_sha256, package_display_name, verification_scope, observed_content_sha256) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",id,oldRevision+1,ticket.operationBytes(),1,ascii(ticket.fingerprint()),1,1,ascii(artifact.manifestSha256()),artifact.author().header().packageDisplayName(),ascii("PARTITION"),null);
        execute("INSERT INTO rule_package_installation_partition (release_id, installation_revision, partition_key) VALUES (?, ?, ?)",id,oldRevision+1,ascii("character.language"));
        execute("INSERT INTO rule_package_installation_partition (release_id, installation_revision, partition_key) VALUES (?, ?, ?)",id,oldRevision+1,ascii("character.tool"));
        execute("UPDATE rule_installation_control SET metadata_row_count = ?, row_version = ? WHERE control_id = 1 AND protocol_version = 1 AND metadata_row_count = ? AND row_version = ?",count,before.control().version()+1,before.control().count(),before.control().version());
        State after=read(new Control(count,before.control().version()+1),0);
        verifyAfter(before,after,ticket,artifact,id);return new Installed(after.roots().get(id),after.operations().get(ticket.operationId()));
    }
    private static void verifyAfter(State before,State after,OperationTicket ticket,RuleArtifact artifact,long id) {
        Root current=after.roots().get(id);Fact fact=after.operations().get(ticket.operationId());
        if(current==null || !current.isTarget() || current.revision()!=ticket.expectedRevision()+1 || !current.status().equals("DRAFT")
                || current.canonical()!=2 || current.archive()!=2 || current.digest()!=null || current.releasedAt()!=null
                || fact==null || !fact.fingerprint().equals(ticket.fingerprint()) || fact.releaseId()!=id || fact.revision()!=current.revision()
                || fact.authorVersion()!=1 || fact.manifestVersion()!=1 || !fact.manifest().equals(artifact.manifestSha256())
                || !fact.name().equals(artifact.author().header().packageDisplayName()) || !fact.scope().equals("PARTITION") || fact.observed()!=null
                || !artifact.author().languages().equals(after.languages().get(id))
                || !artifact.author().tools().equals(after.tools().get(id)))throw bad();
        Root previous=before.roots().get(id);
        if(previous!=null && !previous.createdAt().equals(current.createdAt()))throw bad();
        for(var entry:before.roots().entrySet())if(entry.getKey()!=id && !entry.getValue().equals(after.roots().get(entry.getKey())))throw bad();
        for(var entry:before.languages().entrySet())if(entry.getKey()!=id && !entry.getValue().equals(after.languages().get(entry.getKey())))throw bad();
        for(var entry:before.tools().entrySet())if(entry.getKey()!=id && !entry.getValue().equals(after.tools().get(entry.getKey())))throw bad();
        for(var entry:before.facts().entrySet())if(!entry.getValue().equals(after.facts().get(entry.getKey())))throw bad();
        if(after.roots().size()!=before.roots().size()+(previous==null?1:0) || after.facts().size()!=before.facts().size()+1
                || !after.partitions().containsAll(before.partitions()) || after.partitions().size()!=before.partitions().size()+2)throw bad();
    }
    private List<Object[]> bounded(String sql)throws SQLException {var r=rows(sql,BUDGET+1);if(r.size()>BUDGET)throw bad();return r;}
    List<Object[]> rows(String sql,int max,Object... parameters)throws SQLException {
        try(var statement=connection.prepareStatement(sql)) {
            statement.setQueryTimeout(5);statement.setMaxRows(max);bind(statement,parameters);
            try(var result=statement.executeQuery()) {
                List<Object[]> rows=new ArrayList<>();int columns=result.getMetaData().getColumnCount();
                while(result.next()) {
                    if(rows.size()>=max)throw bad();Object[] row=new Object[columns];
                    for(int i=0;i<columns;i++)row[i]=result.getObject(i+1);rows.add(row);
                }
                return rows;
            }
        }
    }
    private void execute(String sql,Object... parameters)throws SQLException {
        try(var statement=connection.prepareStatement(sql)) {statement.setQueryTimeout(5);bind(statement,parameters);if(statement.executeUpdate()!=1)throw bad();}
    }
    private static void bind(PreparedStatement statement,Object[] parameters)throws SQLException {
        for(int i=0;i<parameters.length;i++) {
            Object value=parameters[i];
            if(value==null)statement.setNull(i+1,Types.VARBINARY);
            else if(value instanceof byte[] bytes)statement.setBytes(i+1,bytes);
            else if(value instanceof String text)statement.setString(i+1,text);
            else if(value instanceof Integer integer)statement.setInt(i+1,integer);
            else if(value instanceof Long number)statement.setLong(i+1,number);
            else throw bad();
        }
    }
    private static Control control(Object[] row) {if(integer(row[0])!=1||integer(row[1])!=1)throw bad();return new Control(integer(row[2]),integer(row[3]));}
    static long integer(Object value) {if(!(value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long))throw bad();return ((Number)value).longValue();}
    static int positiveInt(Object value) {long n=positive(value);if(n>Integer.MAX_VALUE)throw bad();return (int)n;}
    static long positive(Object value) {long n=integer(value);if(n<=0)throw bad();return n;}
    static String string(Object value) {if(!(value instanceof String text))throw bad();return text;}
    static byte[] bytes(Object value) {if(!(value instanceof byte[] bytes))throw bad();return bytes;}
    static String binary(Object value) {byte[] bytes=bytes(value);for(byte b:bytes)if(b<0)throw bad();return new String(bytes,StandardCharsets.US_ASCII);}
    static String nullableBinary(Object value) {return value==null?null:binary(value);}
    static java.time.LocalDateTime timestamp(Object value) {
        if(value instanceof Timestamp timestamp)return timestamp.toLocalDateTime();
        if(value instanceof java.time.LocalDateTime timestamp)return timestamp;
        throw bad();
    }
    static java.time.LocalDateTime nullableTimestamp(Object value) {return value==null?null:timestamp(value);}
    static byte[] ascii(String text) {return text.getBytes(StandardCharsets.US_ASCII);}
    static byte[] nullableAscii(String text) {return text==null?null:ascii(text);}
    static void text(String text,int max) {
        OfflineJson.scalars(text);
        if(text.isEmpty() || text.codePointCount(0,text.length())>max || !Normalizer.isNormalized(text,Normalizer.Form.NFC)
                || text.codePoints().anyMatch(c->c<32||c>=127&&c<=159)
                || text.codePoints().allMatch(c->Character.isWhitespace(c)||Character.isSpaceChar(c)))throw bad();
    }
    static IllegalStateException bad() {return new IllegalStateException("Untrusted or unsupported rule source state");}
    record Control(long count,long version) {Control {if(count<1||count>BUDGET||version<0)throw bad();}}
    record Root(long id,String key,String release,int canonical,int archive,String algorithm,String digest,String status,long revision,java.time.LocalDateTime createdAt,java.time.LocalDateTime releasedAt) {
        boolean isTarget(){return key.equals("dnd5e2014_srd51_se")&&release.equals("1");}
    }
    record Revision(long id,long revision) { }
    record Partition(Revision revision,String key) { }
    record Installed(Root root,Fact fact) { }
    record Fact(long releaseId,long revision,UUID operation,String fingerprint,int authorVersion,int manifestVersion,String manifest,String name,String scope,String observed,java.time.LocalDateTime installedAt) { }
    record State(Control control,Map<Long,Root> roots,Map<Revision,Fact> facts,Map<UUID,Fact> operations,Set<Partition> partitions,Map<Long,LanguagePartition> languages,Map<Long,ToolPartition> tools) {
        Root target(){return roots.values().stream().filter(Root::isTarget).findFirst().orElse(null);}
    }
}
