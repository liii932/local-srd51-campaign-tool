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
    public record CharacterRow(CharacterFact fact, long initialHp, long version, long createdSequence) { }
    public record CommandReceipt(String requestId, String intent, String kind, long resultRevision,
                                 long sequence, String characterId, long beforeHp, long afterHp) { }
    public record SaveReceipt(String requestId, String intent, String fileKey, long revision, String rawDigest,
                              long byteSize, String purpose, boolean accepted) { }

    public void initialize(Connection c, Control control, List<String> modules, Snapshot snapshot) throws SQLException {
        update(c, "INSERT INTO core_player_control VALUES (1,?)", "{}");
        update(c, "INSERT INTO core_control VALUES (1,?,?,?,?,?,?,?,?)", uuid(control.runId()), uuid(control.campaignId()), control.name(), control.revision(), control.epoch(), control.sealed(), control.firstSaved(), hex(control.schemaDigest()));
        for (int i = 0; i < modules.size(); i++) update(c, "INSERT INTO core_storage_module VALUES (?,?)", (long) i, modules.get(i));
        for (int i = 0; i < snapshot.rules().size(); i++) update(c, "INSERT INTO rules_binding VALUES (?,?,?)", (long) i, snapshot.rules().get(i), hex(CampaignRules.materialDigest(snapshot.rules().get(i))));
        update(c, "INSERT INTO rules_vitality VALUES (1,?,?,1,?)", CampaignRules.ALGORITHM, CampaignRules.MAXIMUM_HP, snapshot.rules().contains(CampaignRules.HEALING));
        if (snapshot.domains() != null) { new CampaignDomainRepository().write(c, snapshot.domains()); return; }
        Map<String, EventFact> created = new HashMap<>(); Map<String, Long> versions = new HashMap<>();
        for (EventFact event : snapshot.history()) {
            if (event.type().equals("CHARACTER_CREATED")) created.put(event.characterId(), event);
            versions.merge(event.characterId(), 1L, Long::sum);
        }
        for (CharacterFact character : snapshot.characters()) {
            EventFact origin = created.get(character.id());
            insertCharacter(c, new CharacterRow(character, origin.afterHp(), versions.get(character.id()), origin.sequence()));
        }
        for (EventFact event : snapshot.history()) insertEvent(c, event);
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
    public List<String> verifyRules(Connection c) throws SQLException {
        List<String> result = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM rules_binding ORDER BY ordinal")) {
            while (r.next()) {
                String id = text(r, "module_id");
                if (r.getLong("ordinal") != result.size() || !CampaignRules.materialDigest(id).equals(digest(r.getBytes("material_digest")))) throw integrity("Frozen bundled rule binding differs");
                result.add(id);
            }
        }
        CampaignRules.validate(result);
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM rules_vitality")) {
            if (!r.next() || r.getLong("singleton") != 1 || !CampaignRules.ALGORITHM.equals(text(r, "algorithm")) || r.getLong("maximum_hp_limit") != CampaignRules.MAXIMUM_HP || r.getLong("minimum_current_hp") != 1 || bool(r, "healing_enabled") != result.contains(CampaignRules.HEALING) || r.next()) throw integrity("Frozen vitality material differs");
        }
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT kind FROM core_constant")) {
            if (!r.next() || !"vitality-profile-1".equals(text(r, "kind")) || r.next()) throw integrity("Static seed differs");
        }
        return List.copyOf(result);
    }

    public List<CharacterRow> characters(Connection c) throws SQLException {
        List<CharacterRow> result = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM characters_character ORDER BY character_id")) {
            while (r.next()) result.add(new CharacterRow(new CharacterFact(uuid(r.getBytes("character_id")), text(r, "name"), r.getLong("maximum_hp"), r.getLong("current_hp")), r.getLong("initial_hp"), r.getLong("version"), r.getLong("created_sequence")));
        }
        return List.copyOf(result);
    }
    public Snapshot snapshot(Connection c) throws SQLException {
        Control control = control(c); List<String> rules = verifyRules(c); List<CharacterRow> rows = characters(c);
        if (rules.contains(CampaignRules.RUNTIME)) {
            if (!rows.isEmpty()) throw integrity("Runtime campaign has a second character authority");
            for (String table : List.of("history_event", "history_character_created", "health_healing"))
                try (Statement statement = c.createStatement(); ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    if (!result.next() || result.getLong(1) != 0) throw integrity("Runtime campaign contains unowned finite-model facts");
                }
            var domains = new CampaignDomainRepository().read(c);
            var snapshot = CampaignData.validate(new Snapshot(control.campaignId(), control.name(), rules, List.of(), domains.roots().stream().map(CampaignDomainValidation::summary).toList(), domains));
            verifyDomainReceipts(c, control.revision(), domains.roots());
            verifySaveReceipts(c, control.revision());
            return snapshot;
        }
        Map<Long, EventFact> created = new HashMap<>(); Map<Long, long[]> healing = new HashMap<>();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM history_character_created")) {
            while (r.next()) created.put(r.getLong("sequence"), new EventFact(r.getLong("sequence"), "CHARACTER_CREATED", "", r.getLong("initial_hp"), r.getLong("initial_hp"), 0, 0, text(r, "name"), r.getLong("maximum_hp")));
        }
        if (rules.contains(CampaignRules.HEALING)) {
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM health_healing")) {
                while (r.next()) healing.put(r.getLong("sequence"), new long[]{r.getLong("before_hp"), r.getLong("after_hp"), r.getLong("amount"), r.getLong("applied")});
            }
        }
        List<EventFact> events = new ArrayList<>(); Map<String, Long> versions = new HashMap<>(); Map<String, EventFact> origins = new HashMap<>();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM history_event ORDER BY sequence")) {
            while (r.next()) {
                long sequence = r.getLong("sequence"); String type = text(r, "event_type"), id = uuid(r.getBytes("character_id")); EventFact event;
                if (type.equals("CHARACTER_CREATED")) {
                    EventFact body = created.remove(sequence);
                    if (body == null || healing.containsKey(sequence)) throw integrity("Root creation history body differs");
                    event = new EventFact(sequence, type, id, body.beforeHp(), body.afterHp(), 0, 0, body.name(), body.maximumHp()); origins.put(id, event);
                } else if (type.equals("HEALED")) {
                    long[] body = healing.remove(sequence);
                    if (body == null || created.containsKey(sequence)) throw integrity("Root healing history body differs");
                    event = new EventFact(sequence, type, id, body[0], body[1], body[2], body[3], "", 0);
                } else throw integrity("Unknown root history event");
                events.add(event); versions.merge(id, 1L, Long::sum);
            }
        }
        if (!created.isEmpty() || !healing.isEmpty()) throw integrity("Unowned history body");
        for (CharacterRow row : rows) {
            EventFact origin = origins.get(row.fact().id());
            if (origin == null || origin.afterHp() != row.initialHp() || origin.sequence() != row.createdSequence() || !Objects.equals(versions.get(row.fact().id()), row.version())) throw integrity("Character version or initial facts differ from history");
        }
        Snapshot snapshot = CampaignData.validate(new Snapshot(control.campaignId(), control.name(), rules, rows.stream().map(CharacterRow::fact).toList(), events));
        verifyReceipts(c, control.revision(), events);
        return snapshot;
    }

    private void verifyReceipts(Connection c, long revision, List<EventFact> events) throws SQLException {
        long count = 0;
        long importedEvents = events.size() - revision;
        if (importedEvents < 0) throw integrity("Revision exceeds history");
        Map<String, Long> characterVersions = new HashMap<>();
        for (int i = 0; i < importedEvents; i++) characterVersions.merge(events.get(i).characterId(), 1L, Long::sum);
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM core_command ORDER BY result_revision")) {
            while (r.next()) {
                uuid(r.getBytes("request_id")); String storedIntent = digest(r.getBytes("intent")), kind = text(r, "kind");
                if (!Set.of("ADD_CHARACTER", "HEAL").contains(kind) || r.getLong("result_revision") != ++count || count > revision) throw integrity("Command receipt sequence differs");
                EventFact event = events.get(Math.toIntExact(importedEvents + count - 1));
                if (event.sequence() != r.getLong("event_sequence") || !event.characterId().equals(uuid(r.getBytes("character_id"))) || event.beforeHp() != r.getLong("before_hp") || event.afterHp() != r.getLong("after_hp") || !event.type().equals(kind.equals("HEAL") ? "HEALED" : "CHARACTER_CREATED")) throw integrity("Original command result differs from its exact accepted root event");
                long previousCharacterVersion = characterVersions.getOrDefault(event.characterId(), 0L);
                String expectedIntent = kind.equals("HEAL")
                        ? commandIntent(kind, count - 1, event.characterId(), previousCharacterVersion, event.amount())
                        : commandIntent(kind, count - 1, event.name(), event.maximumHp(), event.afterHp());
                if (!storedIntent.equals(expectedIntent)) throw integrity("Original command intent differs from its accepted event and pre-command CAS");
                characterVersions.merge(event.characterId(), 1L, Long::sum);
            }
        }
        if (count != revision) throw integrity("Revision differs from accepted command receipts");
        verifySaveReceipts(c, revision);
    }
    private void verifyDomainReceipts(Connection c, long revision, List<CampaignDomainData.Root> roots) throws SQLException {
        long imported = roots.size() - revision, count = 0;
        if (imported < 0) throw integrity("Revision exceeds complete domain history");
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM core_command ORDER BY result_revision")) {
            while (r.next()) {
                if (r.getLong("result_revision") != ++count || count > revision || !text(r, "kind").equals("DOMAIN_COMMAND")) throw integrity("Domain receipt sequence differs");
                var root = roots.get(Math.toIntExact(imported + count - 1));
                if (!root.operationId().toString().equals(uuid(r.getBytes("request_id"))) || !root.intent().equals(digest(r.getBytes("intent")))
                        || root.sequence() != r.getLong("event_sequence") || !root.subjectId().toString().equals(uuid(r.getBytes("character_id")))
                        || r.getLong("before_hp") != 0 || r.getLong("after_hp") != 0) throw integrity("Domain receipt differs from accepted root");
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

    public void insertCharacter(Connection c, CharacterRow row) throws SQLException {
        CharacterFact f = row.fact();
        update(c, "INSERT INTO characters_character VALUES (?,?,?,?,?,?,?)", uuid(f.id()), f.name(), f.maximumHp(), f.currentHp(), row.initialHp(), row.version(), row.createdSequence());
    }
    public void updateCharacterHp(Connection c, String id, long beforeVersion, long hp) throws SQLException {
        if (update(c, "UPDATE characters_character SET current_hp=?,version=version+1 WHERE character_id=? AND version=?", hp, uuid(id), beforeVersion) != 1) throw new StorageException("STALE_CHARACTER", "Character version changed");
    }
    public void insertEvent(Connection c, EventFact event) throws SQLException {
        update(c, "INSERT INTO history_event VALUES (?,?,?)", event.sequence(), event.type(), uuid(event.characterId()));
        if (event.type().equals("CHARACTER_CREATED")) update(c, "INSERT INTO history_character_created VALUES (?,?,?,?)", event.sequence(), event.name(), event.maximumHp(), event.afterHp());
        else if (event.type().equals("HEALED")) update(c, "INSERT INTO health_healing VALUES (?,?,?,?,?)", event.sequence(), event.beforeHp(), event.afterHp(), event.amount(), event.applied());
        else throw integrity("Unsupported event producer");
    }
    public CommandReceipt command(Connection c, String requestId) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT * FROM core_command WHERE request_id=?")) {
            s.setBytes(1, uuid(requestId));
            try (ResultSet r = s.executeQuery()) { return r.next() ? new CommandReceipt(requestId, digest(r.getBytes("intent")), text(r, "kind"), r.getLong("result_revision"), r.getLong("event_sequence"), uuid(r.getBytes("character_id")), r.getLong("before_hp"), r.getLong("after_hp")) : null; }
        }
    }
    public void acceptCommand(Connection c, String requestId, String intent, String kind, long previousRevision, EventFact event) throws SQLException {
        update(c, "INSERT INTO core_command VALUES (?,?,?,?,?,?,?,?)", uuid(requestId), hex(intent), kind, Math.addExact(previousRevision, 1), event.sequence(), uuid(event.characterId()), event.beforeHp(), event.afterHp());
        if (update(c, "UPDATE core_control SET revision=revision+1 WHERE singleton=1 AND revision=? AND sealed=0", previousRevision) != 1) throw new StorageException("STALE_REVISION", "Campaign revision changed");
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
