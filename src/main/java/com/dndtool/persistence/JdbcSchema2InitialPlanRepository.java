package com.dndtool.persistence;

import com.dndtool.module.CharacterProficiencyGrant.GrantKind;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Snapshot;
import com.dndtool.service.Schema2InitialCharacterPlanService;
import com.dndtool.service.Schema2InitialCharacterPlanService.InitialPlan;
import com.dndtool.service.Schema2InitialCharacterPlanService.Plan;
import com.dndtool.service.Schema2InitialProficiencyRules.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Append-only INTERNAL initialization preparation; never creates business characters or history. */
public final class JdbcSchema2InitialPlanRepository {
    private final Schema2InitialCharacterPlanService planner = new Schema2InitialCharacterPlanService();

    /** The snapshot and immutable transfer receipt must exist in the same caller transaction. */
    public void store(Connection connection, UUID operationId, InitialPlan initial) throws SQLException {
        BusinessWriteTransactions.requireGuarded(connection);
        Plan plan = initial.character();
        planner.validate(connection, plan.identity(), plan.binding(), plan);
        Sources source = plan.sources();
        insert(connection, "s2_initial_plan", "operation_id, run_id, snapshot_id, campaign_id, character_id, race_module, race_key, subrace_module, subrace_key, background_module, background_key, class_module, class_key, slot_count, choice_count, grant_count",
                bytes(operationId), plan.identity().runBytes(), plan.identity().snapshotBytes(), bytes(initial.campaignId()), bytes(initial.characterId()),
                ascii(source.race().moduleKey()), ascii(source.race().ruleKey()),
                source.subrace() == null ? null : ascii(source.subrace().moduleKey()),
                source.subrace() == null ? null : ascii(source.subrace().ruleKey()),
                ascii(source.background().moduleKey()), ascii(source.background().ruleKey()),
                ascii(source.characterClass().moduleKey()), ascii(source.characterClass().ruleKey()),
                plan.slots().size(), plan.choices().values().stream().mapToInt(List::size).sum(), plan.grants().size());
        Map<EntryIdentity, Integer> positions = new HashMap<>();
        for (int i = 0; i < plan.slots().size(); i++) {
            SlotFact slot = plan.slots().get(i); EntryIdentity id = slot.slot(); positions.put(id, i);
            boolean fixed = slot.grant() instanceof FixedGrant;
            RuleIdentity target = fixed ? ((FixedGrant) slot.grant()).target() : ((ChoiceGrant) slot.grant()).candidates();
            insert(connection, "s2_initial_plan_slot", "operation_id, slot_index, source_module, source_type, source_key, slot_module, slot_key, category, kind, target_module, target_type, target_key, selection_count, enabled",
                    bytes(operationId), i, ascii(id.rule().moduleKey()), ascii(id.rule().ruleType()), ascii(id.rule().ruleKey()),
                    ascii(id.moduleKey()), ascii(id.entryKey()), ascii(slot.grant().category().name()), ascii(fixed ? "FIXED" : "CHOICE"),
                    ascii(target.moduleKey()), ascii(target.ruleType()), ascii(target.ruleKey()),
                    fixed ? null : ((ChoiceGrant) slot.grant()).count(), slot.enabled() ? 1 : 0);
        }
        int choiceIndex = 0;
        for (var choice : plan.choices().entrySet()) for (var target : choice.getValue())
            insert(connection, "s2_initial_plan_choice", "operation_id, choice_index, slot_index, target_module, target_type, target_key",
                    bytes(operationId), choiceIndex++, positions.get(choice.getKey()), ascii(target.moduleKey()), ascii(target.ruleType()), ascii(target.ruleKey()));
        for (int i = 0; i < plan.grants().size(); i++) {
            GrantFact grant = plan.grants().get(i);
            insert(connection, "s2_initial_plan_grant", "operation_id, grant_index, slot_index, category, kind, target_module, target_type, target_key, suppressed",
                    bytes(operationId), i, positions.get(grant.slot()), ascii(grant.category().name()), ascii(grant.kind().name()),
                    ascii(grant.target().moduleKey()), ascii(grant.target().ruleType()), ascii(grant.target().ruleKey()), grant.suppressed() ? 1 : 0);
        }
    }

    /** Independently reads every stored source, slot, selection and fact, then rebuilds the plan. */
    public InitialPlan read(Connection connection, UUID operationId, Snapshot snapshot) throws SQLException {
        List<Header> headers = query(connection, "s2_initial_plan", operationId, "operation_id", 1, row -> {
            require(Arrays.equals(snapshot.identity().runBytes(), binary(row, "run_id"))
                    && Arrays.equals(snapshot.identity().snapshotBytes(), binary(row, "snapshot_id")));
            Object subraceModule = row.getObject("subrace_module"), subraceKey = row.getObject("subrace_key");
            require((subraceModule == null) == (subraceKey == null));
            Sources sources = new Sources(rule(row, "race", "character.race"),
                    subraceModule == null ? null : rule(row, "subrace", "character.subrace"),
                    rule(row, "background", "character.background"), rule(row, "class", "character.class"));
            int slots = number(row, "slot_count"), choices = number(row, "choice_count"), grants = number(row, "grant_count");
            require(slots >= 0 && slots <= 512 && choices >= 0 && choices <= 256 && grants >= 0 && grants <= 768);
            return new Header(uuid(row, "campaign_id"), uuid(row, "character_id"), sources, slots, choices, grants);
        });
        if (headers.isEmpty()) {
            // FK constraints forbid orphans; still check all leaf tables before trusting absence.
            for (String table : List.of("s2_initial_plan_slot", "s2_initial_plan_choice", "s2_initial_plan_grant"))
                require(query(connection, table, operationId, table.endsWith("slot") ? "slot_index" : table.endsWith("choice") ? "choice_index" : "grant_index", 1, row -> true).isEmpty());
            return null;
        }
        require(headers.size() == 1); Header header = headers.getFirst();
        List<SlotFact> slots = new ArrayList<>();
        query(connection, "s2_initial_plan_slot", operationId, "slot_index", 512, row -> {
            require(number(row, "slot_index") == slots.size());
            EntryIdentity id = new EntryIdentity(new RuleIdentity(ascii(row, "source_module"), ascii(row, "source_type"), ascii(row, "source_key")),
                    "proficiency_grants", ascii(row, "slot_module"), ascii(row, "slot_key"));
            GrantCategory category = GrantCategory.valueOf(ascii(row, "category"));
            RuleIdentity target = target(row); String kind = ascii(row, "kind");
            GrantSpec spec;
            if (kind.equals("FIXED")) { require(row.getObject("selection_count") == null); spec = new FixedGrant(category, target); }
            else { require(kind.equals("CHOICE")); spec = new ChoiceGrant(category, target, number(row, "selection_count")); }
            slots.add(new SlotFact(id, spec, bit(row, "enabled"))); return true;
        });
        Map<EntryIdentity, List<RuleIdentity>> choices = new TreeMap<>(com.dndtool.service.Schema2InitialProficiencyRules.SLOT_ORDER);
        int[] choiceIndex = {0};
        query(connection, "s2_initial_plan_choice", operationId, "choice_index", 256, row -> {
            require(number(row, "choice_index") == choiceIndex[0]++);
            choices.computeIfAbsent(slot(slots, number(row, "slot_index")), ignored -> new ArrayList<>()).add(target(row)); return true;
        });
        List<GrantFact> grants = new ArrayList<>();
        query(connection, "s2_initial_plan_grant", operationId, "grant_index", 768, row -> {
            require(number(row, "grant_index") == grants.size());
            grants.add(new GrantFact(slot(slots, number(row, "slot_index")), GrantCategory.valueOf(ascii(row, "category")),
                    GrantKind.valueOf(ascii(row, "kind")), target(row), bit(row, "suppressed"))); return true;
        });
        require(slots.size() == header.slots && choiceIndex[0] == header.choices && grants.size() == header.grants);
        try {
            Plan plan = new Plan(snapshot.identity(), Schema2RulesBinding.from(snapshot.projection()), header.sources, choices, slots, grants);
            planner.validate(connection, plan.identity(), plan.binding(), plan);
            return new InitialPlan(header.campaign, header.character, plan);
        } catch (RuntimeException malformed) { throw new SQLException("S2_INITIAL_PLAN_CORRUPT", malformed); }
    }

    /** Header completeness is checked against the permanent operation set, not a single lookup. */
    public void verifyOperations(Connection connection, Set<UUID> operationIds) throws SQLException {
        Set<UUID> headers = new HashSet<>();
        try (PreparedStatement statement = statement(connection, "SELECT operation_id FROM s2_initial_plan ORDER BY operation_id", 17);
                ResultSet row = statement.executeQuery()) {
            while (row.next()) require(headers.size() < 16 && headers.add(uuid(row, "operation_id")) && operationIds.contains(uuid(row, "operation_id")));
        }
        for (String table : List.of("s2_initial_plan_slot", "s2_initial_plan_choice", "s2_initial_plan_grant")) {
            try (PreparedStatement statement = statement(connection,
                    "SELECT DISTINCT operation_id FROM " + table + " ORDER BY operation_id LIMIT 17", 17);
                    ResultSet row = statement.executeQuery()) {
                Set<UUID> seen = new HashSet<>();
                while (row.next()) require(seen.size() < 16 && seen.add(uuid(row, "operation_id")) && headers.contains(uuid(row, "operation_id")));
            }
        }
    }

    private static EntryIdentity slot(List<SlotFact> slots, int index) throws SQLException {
        require(index >= 0 && index < slots.size()); return slots.get(index).slot();
    }
    private static RuleIdentity rule(ResultSet row, String prefix, String type) throws SQLException {
        return new RuleIdentity(ascii(row, prefix + "_module"), type, ascii(row, prefix + "_key"));
    }
    private static RuleIdentity target(ResultSet row) throws SQLException {
        return new RuleIdentity(ascii(row, "target_module"), ascii(row, "target_type"), ascii(row, "target_key"));
    }
    private static void insert(Connection connection, String table, String columns, Object... values) throws SQLException {
        String[] names = columns.split(", ");
        try (PreparedStatement statement = statement(connection, "INSERT INTO " + table + " (" + columns + ") VALUES ("
                + String.join(", ", Collections.nCopies(values.length, "?")) + ")", 0)) {
            for (int i = 0; i < values.length; i++) {
                Object value = values[i]; int parameter = i + 1;
                if (value == null) statement.setNull(parameter, names[i].equals("selection_count") ? Types.INTEGER : Types.VARBINARY);
                else if (value instanceof byte[] data) statement.setBytes(parameter, data);
                else if (value instanceof Integer number) statement.setInt(parameter, number);
                else throw new SQLException("S2_INITIAL_PLAN_CORRUPT");
            }
            require(statement.executeUpdate() == 1);
        }
    }
    private static <T> List<T> query(Connection connection, String table, UUID operationId, String order, int maximum, Reader<T> reader) throws SQLException {
        try (PreparedStatement statement = statement(connection, "SELECT * FROM " + table + " WHERE operation_id = ? ORDER BY " + order + " LIMIT " + (maximum + 1), maximum + 1)) {
            statement.setBytes(1, bytes(operationId));
            try (ResultSet row = statement.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (row.next()) {
                    require(result.size() < maximum && Arrays.equals(bytes(operationId), binary(row, "operation_id")));
                    try { result.add(reader.read(row)); }
                    catch (RuntimeException malformed) { throw new SQLException("S2_INITIAL_PLAN_CORRUPT", malformed); }
                }
                return result;
            }
        }
    }
    private static PreparedStatement statement(Connection connection, String sql, int maximum) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try { statement.setQueryTimeout(5); if (maximum > 0) statement.setMaxRows(maximum); return statement; }
        catch (SQLException failure) { try { statement.close(); } catch (SQLException close) { failure.addSuppressed(close); } throw failure; }
    }
    private static byte[] bytes(UUID value) { return ByteBuffer.allocate(16).putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits()).array(); }
    private static UUID uuid(ResultSet row, String key) throws SQLException {
        byte[] data = binary(row, key); require(data.length == 16); ByteBuffer buffer = ByteBuffer.wrap(data);
        UUID id = new UUID(buffer.getLong(), buffer.getLong()); require(id.version() == 4 && id.variant() == 2); return id;
    }
    private static byte[] binary(ResultSet row, String key) throws SQLException {
        Object value = row.getObject(key); require(value instanceof byte[]); return (byte[]) value;
    }
    private static byte[] ascii(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
    private static String ascii(ResultSet row, String key) throws SQLException {
        byte[] bytes = binary(row, key); require(bytes.length >= 1 && bytes.length <= 128);
        for (byte value : bytes) require(value >= 0x21 && value <= 0x7e);
        return new String(bytes, StandardCharsets.US_ASCII);
    }
    private static int number(ResultSet row, String key) throws SQLException {
        Object value = row.getObject(key); require(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long);
        long number = ((Number) value).longValue(); require(number >= 0 && number <= Integer.MAX_VALUE); return (int) number;
    }
    private static boolean bit(ResultSet row, String key) throws SQLException { int value = number(row, key); require(value <= 1); return value == 1; }
    private static void require(boolean valid) throws SQLException { if (!valid) throw new SQLException("S2_INITIAL_PLAN_CORRUPT"); }
    private record Header(UUID campaign, UUID character, Sources sources, int slots, int choices, int grants) { }
    @FunctionalInterface private interface Reader<T> { T read(ResultSet row) throws SQLException; }
}
