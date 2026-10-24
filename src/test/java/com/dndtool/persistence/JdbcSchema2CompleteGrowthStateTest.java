package com.dndtool.persistence;

import com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdbcSchema2CompleteGrowthStateTest {
    private static final JdbcSchema2CharacterRuntimeRepository REPOSITORY = new JdbcSchema2CharacterRuntimeRepository();
    private static final Identity ID = Identity.random();
    private static final UUID CAMPAIGN = UUID.randomUUID(), CHARACTER = UUID.randomUUID(), PREPARATION = UUID.randomUUID();
    private static final RuleIdentity A = rule("base", "character.class", "class.guard"), B = rule("extension", "character.class", "class.guard");
    private static final RuleIdentity SUB = rule("base", "character.subclass", "subclass.guard"), FEAT = rule("base", "character.feat", "feat.grappler");
    private static final RuleIdentity FEATURE_A = rule("base", "character.feature", "feature.training"), FEATURE_B = rule("extension", "character.feature", "feature.training");
    private static final RuleIdentity WEAPON_A = rule("base", "character.proficiency", "proficiency.weapon.training"), WEAPON_B = rule("extension", "character.proficiency", "proficiency.weapon.training");
    private static final EntryIdentity INITIAL = new EntryIdentity(rule("base", "character.class_progression", "progression.guard"), "starting_proficiencies", "base", "weapon");
    private static final EntryIdentity MULTICLASS = new EntryIdentity(rule("extension", "character.class_progression", "progression.guard"), "multiclass_proficiencies", "extension", "weapon");
    private static final Map<String,Integer> ABILITIES = Map.of("ability.strength",15,"ability.dexterity",14,"ability.constitution",13,"ability.intelligence",12,"ability.wisdom",10,"ability.charisma",8);

    @Test void fullTypedGrowthSnapshotsKeepCrossModuleClassDiceFeatureFeatAndGrantOrigins() throws Exception {
        History history = history(); var fixture = seeded();
        try (var transaction = BusinessWriteTransactions.openInternal(fixture.dataSource())) {
            for (Event event : history.events()) REPOSITORY.append(transaction.connection(), event);
            transaction.commit();
        }
        assertEquals(history, REPOSITORY.read(fixture.connection, CHARACTER));
        assertEquals(CHARACTER, REPOSITORY.findByRun(fixture.connection, ID.runId()));
        assertEquals(history, REPOSITORY.readRunCharacter(fixture.connection, ID.runId()));
        assertEquals(2, history.current().classes().size()); assertEquals(2, history.current().hitDicePools().size());
        assertEquals(5, history.current().maximumHitDice()); assertEquals(1, history.current().feats().size());
        assertEquals(2, history.current().proficiencies().size()); assertEquals(3, history.current().proficiencyBonus());
        assertEquals(1, fixture.rows("s2_character_command_ability").size());
        assertEquals(2, fixture.rows("s2_character_command_proficiency").size());
        assertEquals(2, history.current().features().size());
        var restored = seeded();
        try (var transaction = BusinessWriteTransactions.openInternal(restored.dataSource())) { REPOSITORY.restore(transaction.connection(), history); transaction.commit(); }
        assertEquals(history, REPOSITORY.read(restored.connection, CHARACTER));
    }

    @Test void everyNewTypedStateAndCommandTableIsActuallyReadAndFailsClosedOnMissingExtraOrForgedRows() throws Exception {
        List<String> tables = List.of("s2_character_event_class", "s2_character_event_hit_die", "s2_character_event_subclass", "s2_character_event_feature", "s2_character_event_feat", "s2_character_event_proficiency", "s2_character_command_ability", "s2_character_command_proficiency");
        List<Consumer<Schema2RuntimeJdbcFixture>> mutations = new ArrayList<>();
        for (String table : tables) {
            mutations.add(f -> f.rows(table).removeFirst());
            mutations.add(f -> f.rows(table).add(new LinkedHashMap<>(f.rows(table).getFirst())));
            mutations.add(f -> f.rows(table).getFirst().put("character_id", bytes(UUID.randomUUID())));
        }
        mutations.add(f -> f.rows("s2_character_event_class").getLast().put("class_level", 20));
        mutations.add(f -> f.rows("s2_character_event_hit_die").getLast().put("sides", 20));
        mutations.add(f -> f.rows("s2_character_event_feature").getLast().put("owner_type", ascii("character.feat")));
        mutations.add(f -> f.rows("s2_character_event_proficiency").getLast().put("source_type", ascii("character.class")));
        mutations.add(f -> f.rows("s2_character_command_ability").getFirst().put("increase_value", 1));
        for (var mutation : mutations) {
            var fixture = seeded();
            try (var transaction = BusinessWriteTransactions.openInternal(fixture.dataSource())) { REPOSITORY.restore(transaction.connection(), history()); transaction.commit(); }
            mutation.accept(fixture); assertThrows(SQLException.class, () -> REPOSITORY.read(fixture.connection, CHARACTER));
        }
    }

    @Test void allNewStateAndChoiceWriteFailuresRollBackTheirCompleteHistory() throws Exception {
        var good = seeded();
        try (var transaction = BusinessWriteTransactions.openInternal(good.dataSource())) { REPOSITORY.restore(transaction.connection(), history()); transaction.commit(); }
        List<String> writes = good.sql.stream().filter(sql -> sql.startsWith("INSERT INTO ")).toList();
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < writes.size(); index++) {
            String table = writes.get(index).substring("INSERT INTO ".length(), writes.get(index).indexOf(" ("));
            if (!(table.startsWith("s2_character_event_") || table.startsWith("s2_character_command_")) || !seen.add(table)) continue;
            var fixture = seeded(); fixture.failWrite = index + 1;
            try (var transaction = BusinessWriteTransactions.openInternal(fixture.dataSource())) { assertThrows(SQLException.class, () -> REPOSITORY.restore(transaction.connection(), history())); transaction.rollback(); }
            assertTrue(fixture.rows("s2_character_runtime").isEmpty());
            for (String inserted : seen) assertTrue(fixture.rows(inserted).isEmpty());
        }
        assertTrue(seen.contains("s2_character_event_feat")); assertTrue(seen.contains("s2_character_command_proficiency"));
    }

    @Test void aggregateHistoryCapacityIsRejectedBeforeAConsumerCanCallDice() throws Exception {
        State base = state(0, 1, ABILITIES, false);
        List<FeatureState> features = new ArrayList<>();
        for (int index = 0; index < 64; index++) features.add(new FeatureState(rule("base","character.feature",String.format("feature.f%02d",index)),A,1));
        List<ProficiencyState> grants = new ArrayList<>();
        for (int index = 0; index < 64; index++) grants.add(new ProficiencyState(new EntryIdentity(INITIAL.rule(),INITIAL.collectionKey(),"base",String.format("slot_%02d",index)),WEAPON_A,1));
        List<Event> events = new ArrayList<>(); State previous = null;
        for (int version = 0; version < 125; version++) {
            State next = new State(ID,CAMPAIGN,CHARACTER,PREPARATION,A,ABILITIES,1,2,11,11,List.of(new ClassLevel(A,1)),List.of(new HitDicePool(10,1,1)),List.of(),features,List.of(),grants,version,List.of(),new RestState(version,null,null));
            Command command = version == 0 ? new Command("INITIALIZED",null,0,null,null) : new Command("TIME_ADVANCED",null,0,null,null,null,GrowthChoices.EMPTY,new RestInput(null,"NONE",1,0,Map.of()));
            events.add(new Event(UUID.randomUUID(),"a".repeat(64),command,null,previous,next)); previous = next;
        }
        History dense = new History(previous,events);
        State candidate = new State(ID,CAMPAIGN,CHARACTER,PREPARATION,A,ABILITIES,1,2,11,11,base.classes(),base.hitDicePools(),List.of(),features,List.of(),grants,125,List.of(),new RestState(125,null,null));
        assertThrows(SQLException.class, () -> JdbcSchema2CharacterRuntimeRepository.checkCandidateCapacity(dense,candidate,new Command("RESOURCE_SPENT",rule("base","character.resource","resource.energy"),1,null,null)));
    }

    private static History history() {
        var increased = new HashMap<>(ABILITIES); increased.put("ability.constitution",15);
        State one=state(0,1,ABILITIES,false),two=state(1,2,ABILITIES,false),three=state(2,3,increased,false),four=state(3,4,increased,true),five=state(4,5,increased,true);
        return new History(five,List.of(
                event(new Command("INITIALIZED",null,0,null,null,null,new GrowthChoices(SUB,Map.of(),null,Map.of(INITIAL,List.of(WEAPON_A)))) ,null,one),
                event(new Command("CHARACTER_ADVANCED",null,0,null,"FIXED_AVERAGE",B,new GrowthChoices(null,Map.of(),null,Map.of(MULTICLASS,List.of(WEAPON_B)))),one,two),
                event(new Command("CHARACTER_ADVANCED",null,0,null,"FIXED_AVERAGE",B,new GrowthChoices(null,Map.of("ability.constitution",2),null,Map.of())),two,three),
                event(new Command("CHARACTER_ADVANCED",null,0,null,"FIXED_AVERAGE",A,new GrowthChoices(null,Map.of(),FEAT,Map.of())),three,four),
                event(new Command("CHARACTER_ADVANCED",null,0,null,"FIXED_AVERAGE",A,GrowthChoices.EMPTY),four,five)));
    }
    private static State state(long version,int level,Map<String,Integer> abilities,boolean feat) {
        int a = level == 1 ? 1 : level >= 4 ? level-2 : 1, b=level-a;
        List<ClassLevel> classes=b==0?List.of(new ClassLevel(A,a)):List.of(new ClassLevel(A,a),new ClassLevel(B,b));
        List<HitDicePool> dice=b==0?List.of(new HitDicePool(10,a,a)):List.of(new HitDicePool(6,b,b),new HitDicePool(10,a,a));
        List<FeatureState> features=b==0?List.of(new FeatureState(FEATURE_A,A,1)):List.of(new FeatureState(FEATURE_A,A,1),new FeatureState(FEATURE_B,B,1));
        List<ProficiencyState> grants=b==0?List.of(new ProficiencyState(INITIAL,WEAPON_A,1)):List.of(new ProficiencyState(INITIAL,WEAPON_A,1),new ProficiencyState(MULTICLASS,WEAPON_B,2));
        return new State(ID,CAMPAIGN,CHARACTER,PREPARATION,A,abilities,level,2+(level-1)/4,10L+level*6,10L+level*6,classes,dice,List.of(new SubclassState(A,SUB,1)),features,feat?List.of(new FeatState(FEAT,A,2)):List.of(),grants,version,List.of(),RestState.INITIAL);
    }
    private static Event event(Command command,State before,State after){return new Event(UUID.randomUUID(),"a".repeat(64),command,null,before,after);}
    private static RuleIdentity rule(String module,String type,String key){return new RuleIdentity(module,type,key);}
    private static Schema2RuntimeJdbcFixture seeded(){var fixture=new Schema2RuntimeJdbcFixture();fixture.rows("s2_initial_plan").add(new LinkedHashMap<>(Map.of("operation_id",bytes(PREPARATION),"run_id",ID.runBytes(),"snapshot_id",ID.snapshotBytes(),"campaign_id",bytes(CAMPAIGN),"character_id",bytes(CHARACTER))));fixture.dataSource();fixture.saveCommitted();return fixture;}
    private static byte[] ascii(String text){return text.getBytes(java.nio.charset.StandardCharsets.US_ASCII);}
    private static byte[] bytes(UUID id){return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
}
