package com.dndtool.persistence.storage;

import com.dndtool.service.storage.*;
import com.google.gson.JsonArray;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.sql.*;
import java.text.Normalizer;
import java.util.*;
import static com.dndtool.service.storage.CampaignData.*;

/** Typed leaves. Every connection and transaction belongs to the lifecycle coordinator. */
public final class CampaignRepository {
    public record Control(String runId, String campaignId, String name, long revision, long epoch,
                          boolean sealed, boolean firstSaved, String schemaDigest) { }
    public record CommandReceipt(String requestId, String intent, String kind, long resultRevision,
                                 long sequence, String subjectId) { }
    public record SaveReceipt(String requestId, String intent, String fileKey, long revision, String rawDigest,
                              long byteSize, String purpose, boolean accepted) { }

    public void initialize(Connection c, Control control, List<String> modules, Snapshot snapshot) throws SQLException {
        update(c, "INSERT INTO core_player_control VALUES (1,?)", "{}");
        update(c, "INSERT INTO core_control VALUES (1,?,?,?,?,?,?,?,?)", uuid(control.runId()), uuid(control.campaignId()), control.name(), control.revision(), control.epoch(), control.sealed(), control.firstSaved(), hex(control.schemaDigest()));
        for (int i = 0; i < modules.size(); i++) update(c, "INSERT INTO core_storage_module VALUES (?,?)", (long) i, modules.get(i));
        new CampaignDomainRepository().write(c, snapshot.domains());
    }

    public Control control(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM core_control")) {
            if (!r.next() || r.getLong("singleton") != 1) throw integrity("Missing initialization receipt");
            Control control = new Control(uuid(r.getBytes("run_id")), uuid(r.getBytes("campaign_id")), text(r, "name"), r.getLong("revision"), r.getLong("epoch"), bool(r, "sealed"), bool(r, "first_saved"), digest(r.getBytes("schema_digest")));
            if (r.next() || control.revision() < 0 || control.epoch() < 1) throw integrity("Invalid run control");
            CampaignJson.name(control.name()); return control;
        }
    }
    public List<String> modules(Connection c) throws SQLException {
        List<String> result = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM core_storage_module ORDER BY ordinal")) {
            while (r.next()) { if (r.getLong("ordinal") != result.size()) throw integrity("Storage module ordering differs"); result.add(text(r, "module_id")); }
        }
        return List.copyOf(result);
    }
    public Snapshot snapshot(Connection c) throws SQLException {
        Control control = control(c);
        var domains = new CampaignDomainRepository().read(c);
        Snapshot snapshot = CampaignData.validate(new Snapshot(control.campaignId(), control.name(), domains));
        verifyDomainReceipts(c, control.revision(), domains.roots());
        verifySaveReceipts(c, control.revision());
        return snapshot;
    }

    private void verifyDomainReceipts(Connection c, long revision, List<CampaignDomainData.Root> roots) throws SQLException {
        long imported = roots.size() - revision, count = 0;
        if (imported < 0) throw integrity("Revision exceeds complete domain history");
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM core_command ORDER BY result_revision")) {
            while (r.next()) {
                if (r.getLong("result_revision") != ++count || count > revision || !text(r, "kind").equals("DOMAIN_COMMAND")) throw integrity("Domain receipt sequence differs");
                var root = roots.get(Math.toIntExact(imported + count - 1));
                String exactIntent = commandIntent("DOMAIN_COMMAND", count - 1, root.kind(),
                        root.space().request(), CampaignDomainCodec.text(root.actor()));
                if (!root.intent().equals(exactIntent)) throw integrity("Domain request differs from its exact accepted intent");
                if (!root.operationId().toString().equals(uuid(r.getBytes("request_id"))) || !root.intent().equals(digest(r.getBytes("intent")))
                        || root.sequence() != r.getLong("event_sequence") || !root.subjectId().toString().equals(uuid(r.getBytes("subject_id")))) throw integrity("Domain receipt differs from accepted root");
            }
        }
        if (count != revision) throw integrity("Domain revision differs from receipts");
    }
    private void verifySaveReceipts(Connection c, long revision) throws SQLException {
        int pending = 0, initial = 0, finals = 0;
        for (SaveReceipt save : saves(c)) {
            if (save.revision() > revision || save.byteSize() < 1 || save.byteSize() > CampaignJson.MAX_BYTES || !Set.of("INITIAL", "MANUAL", "FINAL").contains(save.purpose())) throw integrity("Invalid save receipt");
            if (save.purpose().equals("INITIAL") && (save.revision() != 0 || ++initial > 1)) throw integrity("Initialization checkpoint identity differs");
            if (save.purpose().equals("FINAL") && (save.revision() != revision || ++finals > 1)) throw integrity("Final checkpoint does not seal the exact current revision");
            if (!save.intent().equals(commandIntent("SAVE_" + save.purpose(), save.revision()))) throw integrity("Save intent differs from its exact purpose and revision");
            if (!save.accepted() && ++pending > 1) throw integrity("Multiple pending saves");
        }
    }

    public CommandReceipt command(Connection c, String requestId) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT * FROM core_command WHERE request_id=?")) {
            s.setBytes(1, uuid(requestId));
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? new CommandReceipt(requestId, digest(r.getBytes("intent")), text(r, "kind"),
                        r.getLong("result_revision"), r.getLong("event_sequence"), uuid(r.getBytes("subject_id"))) : null;
            }
        }
    }
    public void acceptCommand(Connection c, String requestId, String intent, String kind,
            long previousRevision, CampaignDomainData.Root root) throws SQLException {
        update(c, "INSERT INTO core_command VALUES (?,?,?,?,?,?)", uuid(requestId), hex(intent), kind,
                Math.addExact(previousRevision, 1), root.sequence(), uuid(root.subjectId().toString()));
        if (update(c, "UPDATE core_control SET revision=revision+1 WHERE singleton=1 AND revision=? AND sealed=0", previousRevision) != 1)
            throw new StorageException("STALE_REVISION", "Campaign revision changed");
    }
    public void epoch(Connection c, long epoch) throws SQLException { update(c, "UPDATE core_control SET epoch=? WHERE singleton=1 AND sealed=0", epoch); }
    public List<SaveReceipt> saves(Connection c) throws SQLException {
        List<SaveReceipt> result = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM core_save ORDER BY request_id")) {
            while (r.next()) result.add(new SaveReceipt(uuid(r.getBytes("request_id")), digest(r.getBytes("intent")), uuid(r.getBytes("file_key")), r.getLong("revision"), digest(r.getBytes("raw_digest")), r.getLong("byte_size"), text(r, "purpose"), bool(r, "accepted")));
        }
        return List.copyOf(result);
    }
    public void registerSave(Connection c, SaveReceipt receipt) throws SQLException {
        update(c, "INSERT INTO core_save VALUES (?,?,?,?,?,?,?,?)", uuid(receipt.requestId()), hex(receipt.intent()), uuid(receipt.fileKey()), receipt.revision(), hex(receipt.rawDigest()), receipt.byteSize(), receipt.purpose(), receipt.accepted());
    }
    public void acceptSave(Connection c, String requestId, boolean end) throws SQLException {
        if (update(c, "UPDATE core_save SET accepted=1 WHERE request_id=? AND accepted=0", uuid(requestId)) != 1) throw integrity("Missing exact save intent");
        update(c, "UPDATE core_control SET first_saved=1,sealed=? WHERE singleton=1", end);
    }
    public static byte[] uuid(String value) {
        UUID u = UUID.fromString(CampaignJson.uuid(value)); return ByteBuffer.allocate(16).putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits()).array();
    }
    public static String uuid(byte[] value) {
        if (value == null || value.length != 16) throw integrity("Invalid UUID bytes");
        ByteBuffer b = ByteBuffer.wrap(value); return CampaignJson.uuid(new UUID(b.getLong(), b.getLong()).toString());
    }
    private static byte[] hex(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw integrity("Invalid digest"); return HexFormat.of().parseHex(value);
    }
    private static String digest(byte[] value) {
        if (value == null || value.length != 32) throw integrity("Invalid digest bytes"); return HexFormat.of().formatHex(value);
    }
    private static boolean bool(ResultSet r, String field) throws SQLException {
        long value = r.getLong(field); if (r.wasNull() || (value != 0 && value != 1)) throw integrity("Invalid boolean"); return value == 1;
    }
    private static String text(ResultSet r, String field) throws SQLException {
        byte[] bytes = r.getBytes(field);
        if (bytes == null) throw integrity("Missing required text");
        try {
            String value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            CampaignJson.validString(value);
            if (!Normalizer.isNormalized(value, Normalizer.Form.NFC)) throw integrity("Stored text is not NFC");
            return value;
        } catch (CharacterCodingException e) {
            throw new StorageException("STORAGE_INTEGRITY", "Stored text is not strict UTF-8", e);
        } catch (StorageException e) {
            throw new StorageException("STORAGE_INTEGRITY", "Stored text violates the Unicode contract", e);
        }
    }
    private static String commandIntent(String kind, long revision, Object... fields) {
        JsonArray a = new JsonArray(); a.add(kind); a.add(Long.toString(revision));
        for (Object field : fields) a.add(field.toString());
        return CampaignJson.digest("DND-VITALITY-COMMAND", CampaignJson.canonical(a));
    }
    private static int update(Connection c, String sql, Object... values) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                Object v = values[i];
                if (v instanceof byte[] bytes) s.setBytes(i + 1, bytes);
                else if (v instanceof String text) s.setString(i + 1, text);
                else if (v instanceof Long number) s.setLong(i + 1, number);
                else if (v instanceof Boolean bit) s.setInt(i + 1, bit ? 1 : 0);
                else throw new IllegalArgumentException("Unsupported typed binding");
            }
            return s.executeUpdate();
        }
    }
    private static StorageException integrity(String message) { return new StorageException("STORAGE_INTEGRITY", message); }
}
