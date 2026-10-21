package com.dndtool.service;

import com.dndtool.module.composition.*;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Snapshot;
import com.dndtool.service.Schema2InitialCharacterPlanService.Plan;
import com.dndtool.service.Schema2InitialProficiencyRules.*;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static com.dndtool.service.Schema2InitialProficiencyRulesTest.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2InitialCharacterPlanServiceTest {
    private static final Identity IDENTITY = new Identity(UUID.fromString("00112233-4455-4677-8899-aabbccddeeff"),
            UUID.fromString("ffeeddcc-bbaa-4988-8766-554433221100"));

    @Test void actualReadIdentityAndCompleteProductionBindingAreRequiredBeforePlanning() throws Exception {
        var projection = projection(); var binding = Schema2RulesBinding.from(projection); var reads = new AtomicInteger();
        var service = new Schema2InitialCharacterPlanService((connection, identity) -> {
            assertEquals(IDENTITY, identity); reads.incrementAndGet(); return new Snapshot(IDENTITY, projection);
        });
        var plan = service.prepare(null, IDENTITY, binding, request());
        assertEquals(IDENTITY, plan.identity()); assertEquals(binding, plan.binding()); assertEquals(SOURCES, plan.sources());
        assertEquals(new Schema2InitialProficiencyRules().prepare(projection, request()).grants(), plan.grants());
        assertEquals(plan, service.validate(null, IDENTITY, binding, plan)); assertEquals(2, reads.get());
        var wrongSnapshot = new Schema2InitialCharacterPlanService((connection, identity) -> new Snapshot(Identity.random(), projection));
        assertEquals("S2_INITIAL_SNAPSHOT_MISMATCH", assertThrows(IllegalArgumentException.class,
                () -> wrongSnapshot.prepare(null, IDENTITY, binding, request())).getMessage());
        var missing = new Schema2InitialCharacterPlanService((connection, identity) -> null);
        assertThrows(IllegalArgumentException.class, () -> missing.prepare(null, IDENTITY, binding, request()));
    }

    @Test void everyBindingDigestVersionAndMemberOrderMustMatchEvenWhenFinalRulesAreUnchanged() {
        var projection = projection(); var original = Schema2RulesBinding.from(projection);
        var service = new Schema2InitialCharacterPlanService((connection, identity) -> new Snapshot(IDENTITY, projection));
        List<Schema2RulesBinding> changed = new ArrayList<>();
        changed.add(binding(original, original.members(), "0".repeat(64), original.finalRulesSha256(), original.chainSha256()));
        changed.add(binding(original, original.members(), original.compositionSha256(), "0".repeat(64), original.chainSha256()));
        changed.add(binding(original, original.members(), original.compositionSha256(), original.finalRulesSha256(), "0".repeat(64)));
        for (String part : List.of("version", "raw", "logical")) {
            var members = new ArrayList<>(original.members()); var first = members.getFirst();
            members.set(0, new ProductionSchema2Projection.StoredMember(first.moduleKey(), part.equals("version") ? "2" : first.releaseVersion(),
                    first.role(), part.equals("raw") ? "0".repeat(64) : first.rawZipSha256(),
                    part.equals("logical") ? "0".repeat(64) : first.logicalSha256()));
            changed.add(binding(original, members, original.compositionSha256(), original.finalRulesSha256(), original.chainSha256()));
        }
        var reordered = new ArrayList<>(original.members()); Collections.swap(reordered, 1, 2);
        changed.add(binding(original, reordered, original.compositionSha256(), original.finalRulesSha256(), original.chainSha256()));
        for (var binding : changed) assertEquals("S2_RULES_BINDING_MISMATCH", assertThrows(IllegalArgumentException.class,
                () -> service.prepare(null, IDENTITY, binding, request())).getMessage());
    }

    @Test void retainedPlanRebuildRejectsForgedSuppressionSourceSlotMissingFactsAndChangedSelections() throws Exception {
        var projection = projection(); var binding = Schema2RulesBinding.from(projection); var reads = new AtomicInteger();
        var service = new Schema2InitialCharacterPlanService((connection, identity) -> {
            reads.incrementAndGet(); return new Snapshot(IDENTITY, projection);
        });
        var original = service.prepare(null, IDENTITY, binding, request());
        var facts = new ArrayList<>(original.grants()); var first = facts.getFirst();
        facts.set(0, new GrantFact(first.slot(), first.category(), first.kind(), first.target(), !first.suppressed()));
        rejects(service, binding, changed(original, original.sources(), original.choices(), original.slots(), facts));
        facts = new ArrayList<>(original.grants()); facts.removeFirst();
        rejects(service, binding, changed(original, original.sources(), original.choices(), original.slots(), facts));
        facts = new ArrayList<>(original.grants());
        facts.set(0, new GrantFact(slot(RACE, "grants_base", "forged_slot"), first.category(), first.kind(), first.target(), first.suppressed()));
        rejects(service, binding, changed(original, original.sources(), original.choices(), original.slots(), facts));
        facts = new ArrayList<>(original.grants());
        facts.set(0, new GrantFact(first.slot(), first.category(), com.dndtool.module.CharacterProficiencyGrant.GrantKind.CHOICE,
                first.target(), first.suppressed()));
        rejects(service, binding, changed(original, original.sources(), original.choices(), original.slots(), facts));
        facts = new ArrayList<>(original.grants());
        facts.set(0, new GrantFact(first.slot(), first.category(), first.kind(), FOREIGN_COMMON, first.suppressed()));
        rejects(service, binding, changed(original, original.sources(), original.choices(), original.slots(), facts));
        facts = new ArrayList<>(original.grants());
        facts.set(0, new GrantFact(first.slot(), Schema2CompositionKernel.GrantCategory.TOOL, first.kind(), FLUTE, first.suppressed()));
        rejects(service, binding, changed(original, original.sources(), original.choices(), original.slots(), facts));
        facts = new ArrayList<>(original.grants());
        facts.add(new GrantFact(first.slot(), first.category(), first.kind(), FOREIGN_COMMON, false));
        rejects(service, binding, changed(original, original.sources(), original.choices(), original.slots(), facts));
        var slots = new ArrayList<>(original.slots()); var slot = slots.getFirst();
        slots.set(0, new SlotFact(slot.slot(), slot.grant(), !slot.enabled()));
        rejects(service, binding, changed(original, original.sources(), original.choices(), slots, original.grants()));
        var choices = new HashMap<>(original.choices()); choices.put(LANGUAGE_SLOT, List.of(DWARVISH));
        assertThrows(IllegalArgumentException.class, () -> service.validate(null, IDENTITY, binding,
                changed(original, original.sources(), choices, original.slots(), original.grants())));
        var forgedSource = new Sources(rule("grants_extension", "character.race", "race.elf"), null, BACKGROUND, CLASS);
        assertThrows(IllegalArgumentException.class, () -> service.validate(null, IDENTITY, binding,
                changed(original, forgedSource, original.choices(), original.slots(), original.grants())));
        int beforeWrongIdentity = reads.get();
        assertThrows(IllegalArgumentException.class, () -> service.validate(null, Identity.random(), binding, original));
        assertEquals(beforeWrongIdentity, reads.get());
        var changedBinding = binding(binding, binding.members(), "0".repeat(64), binding.finalRulesSha256(), binding.chainSha256());
        rejects(service, binding, new Plan(IDENTITY, changedBinding, original.sources(), original.choices(), original.slots(), original.grants()));
        assertEquals(beforeWrongIdentity, reads.get());
    }

    @Test void callerConnectionIsForwardedToTheReaderWithoutTransactionManagement() throws Exception {
        var projection = projection(); var binding = Schema2RulesBinding.from(projection);
        Connection caller = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> { throw new AssertionError("Plan service must not invoke Connection." + method.getName()); });
        var reads = new AtomicInteger();
        var service = new Schema2InitialCharacterPlanService((connection, identity) -> {
            assertSame(caller, connection); assertEquals(IDENTITY, identity); reads.incrementAndGet();
            return new Snapshot(IDENTITY, projection);
        });
        var plan = service.prepare(caller, IDENTITY, binding, request());
        assertEquals(plan, service.validate(caller, IDENTITY, binding, plan));
        assertEquals(2, reads.get());
    }

    @Test void readFailureNeverPublishesAPlanAndPlanCollectionsRemainImmutable() throws Exception {
        var projection = projection(); var binding = Schema2RulesBinding.from(projection); var failure = new SQLException("broken runtime");
        var broken = new Schema2InitialCharacterPlanService((connection, identity) -> { throw failure; });
        assertSame(failure, assertThrows(SQLException.class, () -> broken.prepare(null, IDENTITY, binding, request())));
        var service = new Schema2InitialCharacterPlanService((connection, identity) -> new Snapshot(IDENTITY, projection));
        var plan = service.prepare(null, IDENTITY, binding, request());
        assertThrows(UnsupportedOperationException.class, () -> plan.choices().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.choices().get(LANGUAGE_SLOT).clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.slots().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.grants().clear());
        var choices = new HashMap<Schema2CompositionKernel.EntryIdentity, List<Schema2CompositionKernel.RuleIdentity>>();
        plan.choices().forEach((slot, targets) -> choices.put(slot, new ArrayList<>(targets)));
        var slots = new ArrayList<>(plan.slots()); var grants = new ArrayList<>(plan.grants());
        var copied = new Plan(plan.identity(), plan.binding(), plan.sources(), choices, slots, grants);
        choices.get(LANGUAGE_SLOT).clear(); choices.clear(); slots.clear(); grants.clear();
        assertEquals(plan, copied);
    }

    private static void rejects(Schema2InitialCharacterPlanService service, Schema2RulesBinding binding, Plan plan) {
        assertEquals("S2_INITIAL_PLAN_MISMATCH", assertThrows(IllegalArgumentException.class,
                () -> service.validate(null, IDENTITY, binding, plan)).getMessage());
    }
    private static Plan changed(Plan original, Sources sources,
            Map<Schema2CompositionKernel.EntryIdentity, List<Schema2CompositionKernel.RuleIdentity>> choices,
            List<SlotFact> slots, List<GrantFact> grants) {
        return new Plan(original.identity(), original.binding(), sources, choices, slots, grants);
    }
    private static Schema2RulesBinding binding(Schema2RulesBinding original, List<ProductionSchema2Projection.StoredMember> members,
            String composition, String rules, String chain) {
        return new Schema2RulesBinding(original.canonicalProfile(), original.compositionAlgorithm(), members, composition, rules, chain);
    }
}
