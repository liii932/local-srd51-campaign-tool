import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.ProductionSchema2Projection.StoredMember;
import com.dndtool.module.composition.Schema2CompositionKernel.Schema2Exception;
import com.dndtool.service.Schema2CharacterCheckRules;
import com.dndtool.service.Schema2CharacterHealthRules;
import com.dndtool.service.Schema2CharacterProgressionRules;
import com.dndtool.service.Schema2CharacterRuntime.CheckInput;
import com.dndtool.service.Schema2CharacterRuntime.Command;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CharacterRuntime.HealthInput;
import com.dndtool.service.Schema2CharacterRuntime.State;
import com.dndtool.service.Schema2InitialCharacterPlanService.InitialPlan;
import com.dndtool.service.Schema2PreparationIdentity;
import com.dndtool.service.storage.CampaignRuleContext;
import com.dndtool.service.storage.CampaignRuleSupport;
import com.dndtool.service.storage.StorageException;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Executes module scenarios through the supplied application's existing consumers. */
public final class ModuleScenario {
    private static final String ADJUDICATION = "The DM confirms the module scenario conditions.";
    private static final Set<String> SCENARIO_FIELDS = Set.of("bundles", "members", "character", "skillCheck",
            "damage", "driver", "cases");

    private record Scenario(List<String> members, List<String> cases, Character character, SkillCheck skillCheck,
            Damage damage) { }
    private record Character(String characterClass, String background, Long hitPoints,
            List<String> languages, List<String> tools) { }
    private record SkillCheck(String skill, String ability, String rejectedAbility, Integer d20, Integer dc,
            Integer abilityModifier, Integer proficiencyBonus, Integer total, Boolean success) { }
    private record Damage(String effect, Integer amount, Long hitPointsAfter) { }
    private record InitialCharacter(InitialPlan plan, State state) { }

    public static void main(String[] args) {
        run(args, Map.of());
    }

    static void run(String[] args, Map<String, Consumer<CampaignRuleContext>> authoredCases) {
        if (args.length < 3) throw new IllegalArgumentException("Expected scenario path, package count and packages");
        Path path = Path.of(args[0]);
        String label = path.getFileName().toString();
        try {
            var scenario = readScenario(path);
            int packageCount = Integer.parseInt(args[1]);
            if (packageCount < 1 || packageCount > args.length - 2)
                throw new IllegalArgumentException("Invalid package count: " + packageCount);
            List<String> inputs = List.of(args).subList(2, args.length);
            var cases = registeredCases(scenario, authoredCases);
            var selected = selectCases(scenario.cases(), inputs.subList(packageCount, inputs.size()), cases.keySet());
            var rules = prepare(scenario, inputs.subList(0, packageCount));
            int failures = 0;
            for (String id : selected) {
                if (!runCase(label + " " + id, cases.get(id), rules)) failures++;
            }
            if (failures > 0) System.exit(1);
        } catch (IOException | RuntimeException | AssertionError failure) {
            reportFailure(label, failure);
            System.exit(1);
        }
    }

    private static boolean runCase(String label, Consumer<CampaignRuleContext> check, CampaignRuleContext rules) {
        long start = System.nanoTime();
        try {
            check.accept(rules);
            System.out.printf("PASS %s (%d ms)%n", label, elapsedMillis(start));
            return true;
        } catch (RuntimeException | AssertionError failure) {
            reportFailure(label, failure);
            return false;
        }
    }

    private static void reportFailure(String label, Throwable failure) {
        String message = failure.getMessage();
        if (failure instanceof Schema2Exception rejected) message = rejected.diagnostic().code() + " " + message;
        if (failure instanceof StorageException rejected) message = rejected.code() + " " + message;
        if (message == null) message = failure.getClass().getSimpleName();
        System.err.println("FAIL " + label + ": " + message);
    }

    private static Scenario readScenario(Path path) throws IOException {
        var gson = new GsonBuilder().setStrictness(Strictness.STRICT).create();
        try (var reader = Files.newBufferedReader(path)) {
            var document = gson.fromJson(reader, JsonObject.class);
            if (document == null) throw new IllegalArgumentException("Expected a scenario JSON object");
            for (String field : document.keySet()) {
                if (!SCENARIO_FIELDS.contains(field)) {
                    throw new IllegalArgumentException("Unknown scenario field: " + field);
                }
            }
            var cases = document.get("cases");
            if (cases == null || !cases.isJsonArray() || cases.getAsJsonArray().isEmpty())
                throw new IllegalArgumentException("Scenario requires a nonempty list of case IDs");
            for (var id : cases.getAsJsonArray()) {
                if (!id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString()
                        || !id.getAsString().matches("[a-z][a-z0-9-]*"))
                    throw new IllegalArgumentException("Invalid declared case ID: " + id);
            }
            var scenario = gson.fromJson(document, Scenario.class);
            if (scenario.members() == null || scenario.members().isEmpty())
                throw new IllegalArgumentException("Scenario requires ordered members");
            return scenario;
        }
    }

    private static Map<String, Consumer<CampaignRuleContext>> registeredCases(Scenario scenario,
            Map<String, Consumer<CampaignRuleContext>> authoredCases) {
        Map<String, Consumer<CampaignRuleContext>> cases = new HashMap<>();
        cases.put("initial-character", rules -> verifyCharacter(rules, scenario.character()));
        cases.put("skill-check", rules -> verifySkill(rules, initialCharacter(rules, scenario.character()).state(),
                scenario.skillCheck()));
        cases.put("fixed-damage", rules -> verifyDamage(rules, initialCharacter(rules, scenario.character()).state(),
                scenario.damage()));
        authoredCases.forEach((id, check) -> {
            if (cases.putIfAbsent(id, check) != null) throw new IllegalArgumentException("Duplicate case handler: " + id);
        });
        return cases;
    }

    private static List<String> selectCases(List<String> declared, List<String> requested, Set<String> registered) {
        validateCases(declared, registered, "declared");
        validateCases(requested, Set.copyOf(declared), "selected");
        return requested.isEmpty() ? declared : requested;
    }

    private static void validateCases(List<String> cases, Set<String> available, String scope) {
        var seen = new HashSet<String>();
        for (String id : cases) {
            if (id.isBlank()) throw new IllegalArgumentException("Empty " + scope + " case ID");
            if (!available.contains(id))
                throw new IllegalArgumentException("Unknown " + scope + " case ID: " + id);
            if (!seen.add(id)) throw new IllegalArgumentException("Duplicate " + scope + " case ID: " + id);
        }
    }

    private static CampaignRuleContext prepare(Scenario scenario, List<String> bundlePaths) throws IOException {
        long loadStart = System.nanoTime();
        var packages = new ArrayList<ProductionRuleArtifact>();
        for (String path : bundlePaths) {
            Path bundle = Path.of(path);
            try (var input = Files.newInputStream(bundle)) {
                packages.add(ProductionRuleArtifact.read(input.readNBytes(ProductionRuleArtifact.MAX_ZIP_BYTES + 1)));
            } catch (Schema2Exception rejected) {
                throw new IllegalArgumentException(bundle + " " + rejected.diagnostic().code()
                        + " " + rejected.getMessage(), rejected);
            }
        }
        long loadMillis = elapsedMillis(loadStart);
        var members = packages.stream().map(pack -> pack.moduleKey() + "@" + pack.releaseVersion()).toList();
        expect("ordered members", scenario.members(), members);
        long composeStart = System.nanoTime();
        var rules = CampaignRuleSupport.validate(ProductionSchema2Projection.compose(packages));
        List<StoredMember> actual = rules.projection().members();
        expect("ordered members in runtime composition", members,
                actual.stream().map(member -> member.moduleKey() + "@" + member.releaseVersion()).toList());
        expect("ordered raw ZIP digests in runtime composition", packages.stream().map(ProductionRuleArtifact::rawZipSha256).toList(),
                actual.stream().map(StoredMember::rawZipSha256).toList());
        System.out.printf("Composition ready (%d ms load, %d ms compose and validate)%n", loadMillis, elapsedMillis(composeStart));
        for (int index = 0; index < actual.size(); index++) {
            Path path = Path.of(bundlePaths.get(index));
            String source = path.isAbsolute() ? path.toString() : "WAR bundle " + path.getFileName();
            System.out.printf("MODULE %s SHA-256=%s %s%n", members.get(index), actual.get(index).rawZipSha256(), source);
        }
        return rules;
    }

    private static long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static InitialCharacter initialCharacter(CampaignRuleContext rules, Character character) {
        if (character == null || character.characterClass() == null || character.background() == null)
            throw new IllegalArgumentException("Scenario character requires characterClass and background");
        var plan = rules.initialPlan(UUID.randomUUID(), UUID.randomUUID(), Schema2PreparationIdentity.random(),
                character.characterClass(), character.background());
        State initial = new Schema2CharacterProgressionRules().initial(rules.projection(), plan, UUID.randomUUID(),
                rules.initialAbilities(character.characterClass()), rules.initialChoices(character.characterClass()));
        return new InitialCharacter(plan, initial);
    }

    private static void verifyCharacter(CampaignRuleContext rules, Character character) {
        var initial = initialCharacter(rules, character);
        expect("initial hit points", character.hitPoints(), initial.state().hitPoints());
        expect("granted languages", sorted(character.languages()), grants(initial.plan(), "character.language"));
        expect("granted tools", sorted(character.tools()), grants(initial.plan(), "character.tool"));
    }

    private static List<String> grants(InitialPlan plan, String type) {
        return plan.character().grants().stream().filter(grant -> !grant.suppressed())
                .map(grant -> grant.target()).filter(target -> target.ruleType().equals(type))
                .map(target -> target.moduleKey() + "/" + target.ruleKey()).sorted().toList();
    }

    private static List<String> sorted(List<String> values) {
        return values == null ? null : values.stream().sorted().toList();
    }

    private static void verifySkill(CampaignRuleContext rules, State initial, SkillCheck scenario) {
        if (scenario == null || scenario.dc() == null || scenario.d20() == null)
            throw new IllegalArgumentException("skillCheck requires dc and d20");
        var checks = new Schema2CharacterCheckRules();
        var prepared = checks.prepare(rules.projection(), initial, check(rules, scenario, scenario.ability()));
        var result = checks.resolve(prepared, scenario.d20(), null);
        expect("skill ability modifier", scenario.abilityModifier(), result.abilityModifier());
        expect("skill proficiency bonus", scenario.proficiencyBonus(), result.proficiencyBonus());
        expect("skill check total", scenario.total(), result.total());
        expect("skill check success", scenario.success(), result.success());
        if (scenario.rejectedAbility() == null) return;
        var invalid = check(rules, scenario, scenario.rejectedAbility());
        try {
            checks.prepare(rules.projection(), initial, invalid);
        } catch (IllegalArgumentException rejection) {
            expect("wrong skill ability rejection", "S2_CHECK_SKILL_UNSUPPORTED", rejection.getMessage());
            return;
        }
        throw new AssertionError("The skill check accepted " + scenario.rejectedAbility());
    }

    private static Command check(CampaignRuleContext rules, SkillCheck scenario, String ability) {
        var input = new CheckInput(rules.checkRule(), "SKILL", ability,
                rules.rule("character.proficiency", scenario.skill()), scenario.dc(), false, false, ADJUDICATION);
        return new Command("CHECK_RESOLVED", null, 0, null, null, null, GrowthChoices.EMPTY, null, input);
    }

    private static void verifyDamage(CampaignRuleContext rules, State initial, Damage scenario) {
        if (scenario == null) throw new IllegalArgumentException("fixed-damage requires damage expectations");
        var engine = new Schema2CharacterHealthRules();
        var input = new HealthInput(rules.rule("adventure.health", "health.srd2014"),
                rules.rule("adventure.vital_effect", scenario.effect()), null, ADJUDICATION);
        var command = new Command("HEALTH_EFFECT_RESOLVED", null, 0, null, null, null, GrowthChoices.EMPTY,
                null, null, null, input);
        var result = engine.resolve(engine.prepare(rules.projection(), initial, command), List.of(), null, null);
        expect("fixed damage rolled", scenario.amount(), result.result().rolledAmount());
        expect("fixed damage applied", scenario.amount(), result.result().appliedAmount());
        expect("target hit points after damage", scenario.hitPointsAfter(), result.state().hitPoints());
    }

    private static void expect(String label, Object expected, Object actual) {
        if (expected == null || !Objects.equals(expected, actual)) {
            throw new AssertionError(label + " expected " + expected + ", got " + actual);
        }
    }
}
