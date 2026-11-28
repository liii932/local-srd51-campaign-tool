package com.dndtool.service;

import com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.service.Schema2CharacterRuntime.*;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Validated character operation and its canonical request digest. */
public record Schema2CharacterRequest(UUID operationId, Schema2PreparationIdentity identity, UUID campaignId, UUID characterId,
        UUID preparationOperationId, Schema2RulesBinding rules, long expectedVersion,
        Command command, Map<String, Integer> initialAbilities) {
    public Schema2CharacterRequest {
        if (identity == null || rules == null || command == null || initialAbilities == null)
            throw rejected("S2_CHARACTER_REQUEST");
        List<UUID> ids = Arrays.asList(operationId, identity.runId(), identity.snapshotId(), campaignId,
                characterId, preparationOperationId);
        if (ids.stream().anyMatch(id -> id == null || id.version() != 4 || id.variant() != 2)
                || new HashSet<>(ids).size() != ids.size()) throw rejected("S2_CHARACTER_IDENTITY");
        if (command.kind().equals("INITIALIZED")) {
            if (expectedVersion != -1 || !initialAbilities.keySet().equals(Set.copyOf(Schema2CharacterRuntime.ABILITIES)))
                throw rejected("S2_CHARACTER_REQUEST");
        } else if (expectedVersion < 0 || !initialAbilities.isEmpty()) throw rejected("S2_CHARACTER_REQUEST");
        if (initialAbilities.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getValue() == null
                || entry.getValue() < 1 || entry.getValue() > 30))
            throw rejected("S2_CHARACTER_REQUEST");
        initialAbilities = Collections.unmodifiableMap(new TreeMap<>(initialAbilities));
        if (command.kind().equals("ITEM_ACQUIRED") && !operationId.equals(command.equipmentInput().itemId()))
            throw rejected("S2_EQUIPMENT_INSTANCE");
    }
    public String digest() { return fingerprint(this); }

    private static IllegalArgumentException rejected(String code) { return new IllegalArgumentException(code); }

    private static String fingerprint(Schema2CharacterRequest request) {
        try {
            var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
            out.write("local-dnd/schema2-character-command/1\0".getBytes(StandardCharsets.US_ASCII));
            for (UUID id : List.of(request.operationId(), request.identity().runId(), request.identity().snapshotId(),
                    request.campaignId(), request.characterId(), request.preparationOperationId())) text(out, id.toString());
            Schema2RulesBinding binding = request.rules();
            text(out, binding.canonicalProfile()); text(out, binding.compositionAlgorithm());
            out.writeInt(binding.members().size());
            for (var member : binding.members()) {
                text(out, member.moduleKey()); text(out, member.releaseVersion()); text(out, member.role().name());
                text(out, member.rawZipSha256()); text(out, member.logicalSha256());
            }
            text(out, binding.compositionSha256()); text(out, binding.finalRulesSha256()); text(out, binding.chainSha256());
            out.writeLong(request.expectedVersion()); Command command = request.command();
            text(out, command.kind()); RuleIdentity resource = command.resource(); out.writeBoolean(resource != null);
            if (resource != null) { text(out, resource.moduleKey()); text(out, resource.ruleType()); text(out, resource.ruleKey()); }
            out.writeLong(command.amount()); text(out, Objects.toString(command.recoveryTrigger(), ""));
            text(out, Objects.toString(command.hpChoice(), ""));
            nullableRule(out, command.targetClass());
            GrowthChoices choices = command.growthChoices();
            nullableRule(out, choices.subclass()); nullableRule(out, choices.feat());
            var increases = new TreeMap<>(choices.abilityIncreases()); out.writeInt(increases.size());
            for (var entry : increases.entrySet()) { text(out, entry.getKey()); out.writeInt(entry.getValue()); }
            var grants = new ArrayList<>(choices.proficiencyChoices().entrySet());
            grants.sort(Map.Entry.comparingByKey(ENTRY_ORDER)); out.writeInt(grants.size());
            for (var grant : grants) {
                EntryIdentity slot = grant.getKey(); rule(out, slot.rule()); text(out, slot.collectionKey());
                text(out, slot.moduleKey()); text(out, slot.entryKey());
                var selected = grant.getValue().stream().sorted(RULE_ORDER).toList(); out.writeInt(selected.size());
                for (RuleIdentity target : selected) rule(out, target);
            }
            RestInput rest = command.restInput(); out.writeBoolean(rest != null);
            if (rest != null) {
                text(out, Objects.toString(rest.restKind(), "")); text(out, Objects.toString(rest.activity(), ""));
                out.writeLong(rest.minutes()); out.writeInt(rest.hitDieSides());
                var recovered = new TreeMap<>(rest.recoveredHitDice()); out.writeInt(recovered.size());
                for (var entry : recovered.entrySet()) { out.writeInt(entry.getKey()); out.writeInt(entry.getValue()); }
            }
            // The command discriminant gives this extension an unambiguous position while
            // preserving every earlier command's persisted request bytes and digest.
            CheckInput check = command.checkInput();
            if (check != null) {
                rule(out, check.rule()); text(out, check.kind()); text(out, check.abilityKey());
                nullableRule(out, check.proficiency()); out.writeInt(check.difficultyClass());
                out.writeBoolean(check.advantage()); out.writeBoolean(check.disadvantage()); text(out, check.adjudication());
                text(out, check.subject());
            }
            EquipmentInput item = command.equipmentInput();
            if (item != null) {
                text(out, item.itemId().toString()); nullableRule(out, item.item());
                text(out, Objects.toString(item.location(), "")); text(out, item.adjudication());
            }
            HealthInput health = command.healthInput();
            if (health != null) {
                rule(out, health.rule()); nullableRule(out, health.effect());
                text(out, Objects.toString(health.temporaryChoice(), "")); text(out, health.adjudication());
            }
            CombatInput combat = command.combatInput();
            if (combat != null) {
                text(out, combat.combatId().toString()); text(out, combat.combatOperationId().toString());
                text(out, combat.frameKey().toString()); text(out, combat.actorId().toString());
                out.writeInt(combat.damageAmount()); out.writeBoolean(combat.critical()); out.writeInt(combat.secondsAdvanced());
            }
            SpellbookInput book=command.spellbookInput();
            if(book!=null) {
                rule(out,book.castingRule()); out.writeInt(book.learned().size());for(RuleIdentity spell:book.learned())rule(out,spell);
                out.writeInt(book.prepared().size());for(RuleIdentity spell:book.prepared())rule(out,spell);
            }
            out.writeInt(request.initialAbilities().size());
            for (var entry : request.initialAbilities().entrySet()) { text(out, entry.getKey()); out.writeInt(entry.getValue()); }
            if (rest != null && !rest.recoveredSpellSlots().isEmpty()) {
                text(out, "SRD_2014_WIZARD_ARCANE_RECOVERY_V1");
                out.writeInt(rest.recoveredSpellSlots().size());
                for (var entry : rest.recoveredSpellSlots().entrySet()) { out.writeInt(entry.getKey()); out.writeInt(entry.getValue()); }
            }
            if (!choices.cantrips().isEmpty()) {
                text(out, "S2_KNOWN_CANTRIP_CHOICES_V1");
                out.writeInt(choices.cantrips().size());
                for (RuleIdentity spell : choices.cantrips()) rule(out, spell);
            }
            if (choices.fightingStyle() != null) {
                text(out, "S2_FIGHTING_STYLE_CHOICE_V1");
                rule(out, choices.fightingStyle());
            }
            if (!choices.expertise().isEmpty()) {
                text(out, "S2_ROGUE_EXPERTISE_CHOICES_V1");
                out.writeInt(choices.expertise().size());
                for (RuleIdentity proficiency : choices.expertise()) rule(out, proficiency);
            }
            out.flush(); return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void text(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); out.writeInt(bytes.length); out.write(bytes);
    }
    private static final Comparator<RuleIdentity> RULE_ORDER = Comparator.comparing(RuleIdentity::moduleKey)
            .thenComparing(RuleIdentity::ruleType).thenComparing(RuleIdentity::ruleKey);
    private static final Comparator<EntryIdentity> ENTRY_ORDER = Comparator.comparing(EntryIdentity::rule, RULE_ORDER)
            .thenComparing(EntryIdentity::collectionKey).thenComparing(EntryIdentity::moduleKey).thenComparing(EntryIdentity::entryKey);
    private static void nullableRule(DataOutputStream out, RuleIdentity identity) throws IOException {
        out.writeBoolean(identity != null); if (identity != null) rule(out, identity);
    }
    private static void rule(DataOutputStream out, RuleIdentity identity) throws IOException {
        text(out, identity.moduleKey()); text(out, identity.ruleType()); text(out, identity.ruleKey());
    }
}
