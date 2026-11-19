package com.dndtool.persistence.storage;

import com.dndtool.service.Schema2CombatHistory;

import com.dndtool.service.storage.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import static com.dndtool.service.storage.CampaignDomainData.*;
import com.dndtool.service.storage.CampaignDomainData.Character;

/** Caller-owned transaction leaves for typed aggregates and independently checked relational projections. */
public final class CampaignDomainRepository {
    private static final List<String> TABLES = List.of("rules_runtime_material", "characters_runtime_character", "scenes_world", "scenes_space", "scenes_clock", "scenes_placement", "monsters_campaign", "combat_scene", "history_runtime_root",
            "resources_character", "equipment_instance", "spellcasting_book", "actions_frame", "effects_instance", "monsters_instance", "manual_character", "participation_state", "participation_member", "participation_control", "participation_share", "participation_exploration");

    public void write(Connection connection, State state) throws SQLException {
        // CampaignStorageService owns an explicit SQLite BEGIN IMMEDIATE. Xerial's
        // JDBC autoCommit flag does not reflect a transaction begun with SQL.
        Map<String, List<List<Object>>> expected = rows(state);
        for (String table : TABLES.reversed()) try (Statement statement = connection.createStatement()) { statement.executeUpdate("DELETE FROM " + table); }
        for (String table : TABLES) insert(connection, table, expected.get(table), "");
    }

    private record RemovedRows(StorageSchemaCatalog.Table table, Set<List<Object>> keys) { }

    /** Update parents before their children and retire obsolete children before their parents. */
    public void applyChanges(Connection connection, StoragePlan plan, State before, State after) throws SQLException {
        var original = rows(before);
        var updated = rows(after);
        List<RemovedRows> removed = new ArrayList<>();
        for (var table : plan.tables()) {
            var oldRows = original.get(table.name());
            var newRows = updated.get(table.name());
            if (oldRows == null || oldRows.equals(newRows)) continue;
            var columns = table.columns().stream().map(StorageSchemaCatalog.Column::name).toList();
            var keyIndices = table.primaryKey().stream().map(columns::indexOf).toList();
            Map<List<Object>, List<Object>> remaining = new LinkedHashMap<>();
            for (var row : oldRows) remaining.put(keyIndices.stream().map(row::get).toList(), row);
            List<List<Object>> changed = new ArrayList<>();
            for (var row : newRows) {
                var prior = remaining.remove(keyIndices.stream().map(row::get).toList());
                if (!row.equals(prior)) changed.add(row);
            }
            if (!changed.isEmpty()) {
                var assignments = columns.stream().filter(column -> !table.primaryKey().contains(column))
                        .map(SqliteSchemaCompiler::quote).map(name -> name + " = excluded." + name).toList();
                String conflict = " ON CONFLICT (" + SqliteSchemaCompiler.columns(table.primaryKey())
                        + ") DO UPDATE SET " + String.join(", ", assignments);
                insert(connection, table.name(), changed, conflict);
            }
            if (!remaining.isEmpty()) removed.add(new RemovedRows(table, remaining.keySet()));
        }
        for (var obsolete : removed.reversed()) {
            var table = obsolete.table();
            var predicates = table.primaryKey().stream().map(key -> SqliteSchemaCompiler.quote(key) + " = ?").toList();
            String sql = "DELETE FROM " + SqliteSchemaCompiler.quote(table.name()) + " WHERE " + String.join(" AND ", predicates);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (var key : obsolete.keys()) {
                    bind(statement, key);
                    if (statement.executeUpdate() != 1) throw new SQLException("DOMAIN_DELETE_COUNT");
                }
            }
        }
    }

    private static void insert(Connection connection, String table, List<List<Object>> rows, String conflict) throws SQLException {
        if (rows.isEmpty()) return;
        String placeholders = String.join(",", Collections.nCopies(rows.getFirst().size(), "?"));
        String sql = "INSERT INTO " + SqliteSchemaCompiler.quote(table) + " VALUES (" + placeholders + ")" + conflict;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (var row : rows) {
                bind(statement, row);
                if (statement.executeUpdate() != 1) throw new SQLException("DOMAIN_INSERT_COUNT");
            }
        }
    }

    private static void bind(PreparedStatement statement, List<Object> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            if (value instanceof UUID uuid) statement.setBytes(i + 1, CampaignRepository.uuid(uuid.toString()));
            else statement.setObject(i + 1, value);
        }
    }
    public State read(Connection connection) throws SQLException {
        Material material = one(connection, "rules_runtime_material", Material.class);
        World world = one(connection, "scenes_world", World.class);
        List<Character> characters = documents(connection, "characters_runtime_character", "character_id", Character.class);
        List<Schema2CombatHistory.History> combats = documents(connection, "combat_scene", "combat_id", Schema2CombatHistory.History.class);
        List<Root> roots = documents(connection, "history_runtime_root", "sequence", Root.class);
        State state = new State(material, characters, combats, world.nodes(), world.positions(), world.items(), roots, world.manual(), one(connection, "participation_state", CampaignParticipationData.State.class), world.space());
        // Check every projected value and every row; recovery never silently rebuilds a damaged index.
        Map<String, List<List<Object>>> expected = rows(state);
        for (String table : TABLES) {
            List<String> actual = new ArrayList<>();
            try (Statement statement = connection.createStatement(); ResultSet results = statement.executeQuery("SELECT * FROM " + table)) {
                int columns = results.getMetaData().getColumnCount();
                while (results.next()) {
                    List<Object> row = new ArrayList<>();
                    for (int i = 1; i <= columns; i++) {
                        Object value = results.getObject(i);
                        if (value instanceof byte[] bytes) value = UUID.fromString(CampaignRepository.uuid(bytes));
                        else if (value instanceof Number number) value = number.longValue();
                        row.add(value);
                    }
                    actual.add(key(row));
                    if (actual.size() > 100000) throw new SQLException("DOMAIN_ROW_BUDGET");
                }
            }
            List<String> wanted = expected.get(table).stream().map(CampaignDomainRepository::key).sorted().toList();
            Collections.sort(actual);
            if (!actual.equals(wanted)) throw new SQLException("DOMAIN_PROJECTION_MISMATCH: " + table);
        }
        return state;
    }
    private static String key(List<Object> row) { return CampaignDomainCodec.text(row); }
    private static <T> T one(Connection c, String table, Class<T> type) throws SQLException {
        List<T> results = documents(c, table, "singleton", type);
        if (results.size() != 1) throw new SQLException("DOMAIN_SINGLETON_MISMATCH");
        return results.getFirst();
    }
    private static <T> List<T> documents(Connection c, String table, String order, Class<T> type) throws SQLException {
        List<T> result = new ArrayList<>();
        try (Statement statement = c.createStatement(); ResultSet rows = statement.executeQuery("SELECT document FROM " + table + " ORDER BY " + order)) {
            while (rows.next()) {
                byte[] bytes = rows.getBytes(1);
                if (bytes == null) throw new SQLException("DOMAIN_DOCUMENT_MISSING");
                T value = CampaignDomainCodec.decode(bytes, type);
                if (!Arrays.equals(bytes, CampaignDomainCodec.encode(value))) throw new SQLException("DOMAIN_DOCUMENT_NOT_CANONICAL");
                result.add(value);
                if (result.size() > 100000) throw new SQLException("DOMAIN_ROW_BUDGET");
            }
        }
        return List.copyOf(result);
    }
    private static Map<String, List<List<Object>>> rows(State state) {
        Map<String, List<List<Object>>> data = new LinkedHashMap<>(); TABLES.forEach(table -> data.put(table, new ArrayList<>()));
        add(data, "rules_runtime_material", 1L, CampaignDomainCodec.text(state.material()));
        add(data, "scenes_world", 1L, CampaignDomainCodec.text(new World(state.nodes(), state.positions(), state.items(), state.manual(), state.space())));
        for (var scene : state.space().scenes()) add(data, "scenes_space", scene.id(), scene.nodeId(), scene.resolution(), scene.mode(), scene.version(), CampaignDomainCodec.text(scene));
        for (var clock : state.space().clocks()) add(data, "scenes_clock", clock.space().kind(), clock.space().id(), clock.second(), clock.version());
        for (var position : state.positions()) add(data, "scenes_placement", position.characterId(), position.space().kind(), position.space().id(), position.version(), CampaignDomainCodec.text(position));
        for (var monster : state.space().monsters()) add(data, "monsters_campaign", monster.id(), (long) monster.hitPoints(), monster.version(), CampaignDomainCodec.text(monster));
        add(data, "participation_state", 1L, CampaignDomainCodec.text(state.participation()));
        for (var member : state.participation().members()) add(data, "participation_member", member.id(), member.active() ? 1L : 0L, member.version(), CampaignDomainCodec.text(member));
        for (var control : state.participation().controls()) add(data, "participation_control", control.characterId(), control.memberId(), control.version(), CampaignDomainCodec.text(control));
        for (var share : state.participation().shares()) add(data, "participation_share", share.id(), share.subjectId(), share.active() ? 1L : 0L, share.version(), CampaignDomainCodec.text(share));
        for (var permission : state.participation().exploration()) add(data, "participation_exploration", permission.characterId(), permission.active() ? 1L : 0L, permission.version(), CampaignDomainCodec.text(permission));
        for (var character : state.manual().characters()) {
            var current = character.current();
            add(data, "manual_character", current.id(), current.name(), current.kind(), current.active() ? 1L : 0L, current.version(), CampaignDomainCodec.text(character));
        }
        for (Character character : state.characters()) {
            var c = character.history().current(); UUID id = character.id();
            add(data, "characters_runtime_character", id, character.name(), character.kind(), character.active() ? 1L : 0L, c.version(), c.hitPoints(), c.maximumHitPoints(), CampaignDomainCodec.text(character));
            for (var r : c.resources()) add(data, "resources_character", id, r.identity().moduleKey() + ":" + r.identity().ruleType() + ":" + r.identity().ruleKey(), r.current(), r.maximum(), r.unlimited() ? 1L : 0L);
            for (var item : c.equipment()) add(data, "equipment_instance", item.itemId(), id, item.location(), CampaignDomainCodec.text(item));
            if (c.spellbook() != null) add(data, "spellcasting_book", id, CampaignDomainCodec.text(c.spellbook()));
        }
        for (var history : state.combats()) {
            var battle = history.current(); UUID id = battle.combatId();
            add(data, "combat_scene", id, battle.status().name(), battle.version(), CampaignDomainCodec.text(history));
            for (var flow : battle.flows()) for (var frame : flow.frames()) add(data, "actions_frame", frame.frameKey(), id, frame.status().name(), CampaignDomainCodec.text(frame));
            for (var effect : battle.ongoingEffects()) add(data, "effects_instance", effect.effectKey(), id, effect.status().name(), CampaignDomainCodec.text(effect));
            for (var monster : battle.monsters()) add(data, "monsters_instance", monster.monsterId(), id, (long) monster.hitPoints(), CampaignDomainCodec.text(monster));
        }
        for (Root root : state.roots()) add(data, "history_runtime_root", root.sequence(), root.operationId(), root.kind(), root.subjectId(), CampaignDomainCodec.text(root));
        return data;
    }
    private static void add(Map<String, List<List<Object>>> rows, String table, Object... values) { rows.get(table).add(Arrays.asList(values)); }
}
