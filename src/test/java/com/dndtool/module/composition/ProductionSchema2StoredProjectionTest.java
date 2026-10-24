package com.dndtool.module.composition;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductionSchema2StoredProjectionTest {
    @ParameterizedTest
    @ValueSource(strings = {"MULTICLASS_PREREQUISITE", "FEATURE_MODE", "PROFICIENCY_CATEGORY"})
    void restoredOriginalDefinitionCannotBeMadeLegalByALaterFieldChange(String mutation) {
        var material = Schema2ProgressionFixtures.fullProjection();
        Field field = switch (mutation) {
            case "FEATURE_MODE" -> Field.EXECUTION_MODE;
            case "PROFICIENCY_CATEGORY" -> Field.CATEGORY;
            default -> Field.MULTICLASS_PREREQUISITE;
        };
        RuleIdentity target = switch (mutation) {
            case "FEATURE_MODE" -> new RuleIdentity(Schema2ProgressionFixtures.BASE, "character.feature", "feature.training");
            case "PROFICIENCY_CATEGORY" -> Schema2ProgressionFixtures.ATHLETICS;
            default -> Schema2ProgressionFixtures.PROGRESSION;
        };
        var chains = new ArrayList<>(material.projection().chains());
        int index = 0; while (!chains.get(index).target().equals(target) || chains.get(index).field() != field) index++;
        var chain = chains.get(index); assertEquals(1, chain.steps().size()); var initial = chain.steps().getFirst();
        boolean omitted = field == Field.MULTICLASS_PREREQUISITE;
        var invalidInitial = omitted ? new FieldState(StateKind.ABSENT, null)
                : new FieldState(StateKind.PRESENT, new TextValue(field == Field.CATEGORY ? "ARMOR" : "BLOCKED"));
        var source = initial.authorLocation();
        var originalSource = omitted ? new Location(source.moduleKey(), source.releaseVersion(), source.rawZipSha256(),
                source.path(), source.pointer().substring(0, source.pointer().lastIndexOf('/')), null) : source;
        var extension = material.members().get(1);
        chains.set(index, new FieldChain(target, field, List.of(
                new ChainStep("initial", initial.before(), invalidInitial, initial.provider(), 0, originalSource,
                        omitted ? "omitted_optional" : "declared"),
                new ChainStep("set_field", invalidInitial, initial.after(), new Provider(extension.moduleKey(), extension.releaseVersion()), 1,
                        new Location(extension.moduleKey(), extension.releaseVersion(), extension.rawZipSha256(), "changes.json", "/2", null), "explicit"))));
        assertEquals("S2_STORED_INVALID", assertThrows(Schema2Exception.class, () -> restore(material,
                material.projection().rules(), chains)).diagnostic().code());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MULTICLASS_PREREQUISITE", "ABILITY_KEY"})
    void onlyAuthorNoteMayBeClearedAfterAnOmittedOptionalDeclaration(String fieldName) {
        Field field = Field.valueOf(fieldName);
        var material = field == Field.MULTICLASS_PREREQUISITE
                ? Schema2ProgressionFixtures.projection() : Schema2ProgressionFixtures.fullProjection();
        RuleIdentity target = field == Field.MULTICLASS_PREREQUISITE
                ? Schema2ProgressionFixtures.PROGRESSION : Schema2ProgressionFixtures.LIGHT_ARMOR;
        var chains = new ArrayList<>(material.projection().chains());
        int index = 0; while (!chains.get(index).target().equals(target) || chains.get(index).field() != field) index++;
        var chain = chains.get(index); var initial = chain.steps().getFirst();
        assertEquals(StateKind.ABSENT, initial.after().state());
        var extension = material.members().get(1); var steps = new ArrayList<>(chain.steps());
        steps.add(new ChainStep("clear_field", initial.after(), initial.after(),
                new Provider(extension.moduleKey(), extension.releaseVersion()), 1,
                new Location(extension.moduleKey(), extension.releaseVersion(), extension.rawZipSha256(), "changes.json", "/2", null), "explicit"));
        chains.set(index, new FieldChain(target, field, steps));
        assertEquals("S2_STORED_INVALID", assertThrows(Schema2Exception.class, () -> restore(material,
                material.projection().rules(), chains)).diagnostic().code());
    }

    @Test void restoresFullTypedRowsToIndependentGoldenBytesWithoutOriginalArtifacts() {
        var expected = ProductionSchema2Projection.compose(ProductionRuleArtifactTest.fixedArtifacts());
        var restored = restore(expected, expected.projection().rules(), expected.projection().chains());
        for (var pair : List.of(new byte[][]{ProductionRuleArtifactTest.productionResource("expected/configuration.json"), restored.configurationJson()},
                new byte[][]{ProductionRuleArtifactTest.productionResource("expected/final-rules.json"), restored.finalRulesJson()},
                new byte[][]{ProductionRuleArtifactTest.productionResource("expected/chains.json"), restored.chainsJson()})) assertArrayEquals(pair[0], pair[1]);
        assertEquals(expected.members(), restored.members());
        assertEquals((long) restored.configurationJson().length + restored.finalRulesJson().length + restored.chainsJson().length, restored.canonicalByteLength());
        assertThrows(UnsupportedOperationException.class, () -> restored.members().clear());
        assertEquals("S2_BASE_INCOMPLETE", assertThrows(Schema2Exception.class,
                () -> new Schema2CompositionKernel().requireProductionQualification(restored.projection())).diagnostic().code());
    }

    @Test void rejectsMissingDuplicateOrReorderedChainsAndBrokenBeforeAfterOrSource() {
        var expected = ProductionSchema2Projection.compose(ProductionRuleArtifactTest.fixedArtifacts());
        for (String mutation : List.of("missing", "duplicate", "reverse", "before", "after", "provider", "raw", "index", "origin")) {
            var chains = new ArrayList<>(expected.projection().chains());
            if (mutation.equals("missing")) chains.removeLast();
            else if (mutation.equals("duplicate")) chains.add(chains.getLast());
            else if (mutation.equals("reverse")) Collections.reverse(chains);
            else {
                var chain = chains.getFirst(); var steps = new ArrayList<>(chain.steps()); var step = steps.getFirst(); var location = step.authorLocation();
                steps.set(0, new ChainStep(step.op(), mutation.equals("before") ? new FieldState(StateKind.ABSENT, null) : step.before(),
                        mutation.equals("after") ? new FieldState(StateKind.UNINITIALIZED, null) : step.after(),
                        mutation.equals("provider") ? new Provider("unknown", "1") : step.provider(), mutation.equals("index") ? 3 : step.loadIndex(),
                        mutation.equals("raw") ? new Location(location.moduleKey(), location.releaseVersion(), "a".repeat(64), location.path(), location.pointer(), null) : location,
                        mutation.equals("origin") ? "explicit" : step.origin()));
                chains.set(0, new FieldChain(chain.target(), chain.field(), steps));
            }
            assertThrows(RuntimeException.class, () -> restore(expected, expected.projection().rules(), chains), mutation);
        }
    }

    @Test void rejectsNoncanonicalTypedFinalRowsAndChangedValuesEvenWhenChainsRemain() {
        var expected = ProductionSchema2Projection.compose(ProductionRuleArtifactTest.fixedArtifacts());
        for (String name : List.of("Changed name", "e\u0301", "bad\u0085", "\ud800", "😀".repeat(121))) {
            var rules = new ArrayList<>(expected.projection().rules());
            int index = 0; while (!(rules.get(index) instanceof FinalLanguage)) index++;
            var old = (FinalLanguage) rules.get(index); var definition = old.definition();
            rules.set(index, new FinalLanguage(new LanguageDefinition(definition.identity(), name, definition.description(), definition.category(), definition.sourcePage(), definition.authorNote()), old.enabled()));
            assertThrows(RuntimeException.class, () -> restore(expected, rules, expected.projection().chains()));
        }
    }

    @Test void chainCannotUseAFutureDeclaredLanguageEvenWhenFinalValueAndProviderChainAgree() {
        var expected = ProductionSchema2Projection.compose(ProductionRuleArtifactTest.fixedArtifacts());
        var chains = new ArrayList<>(expected.projection().chains());
        int index = 0; while (!(chains.get(index).target() instanceof EntryIdentity) || chains.get(index).field() != Field.LANGUAGE) index++;
        var chain = chains.get(index); var initial = chain.steps().getFirst();
        var future = new RuleIdentity("validation_extension", "character.language", "language.elvish");
        var futureState = new FieldState(StateKind.PRESENT, new IdentityValue(future));
        var compatibility = expected.members().get(2);
        var steps = List.of(new ChainStep("initial", initial.before(), futureState, initial.provider(), initial.loadIndex(), initial.authorLocation(), initial.origin()),
                new ChainStep("set_field", futureState, initial.after(), new Provider(compatibility.moduleKey(), compatibility.releaseVersion()), 2,
                        new Location(compatibility.moduleKey(), compatibility.releaseVersion(), compatibility.rawZipSha256(), "changes.json", "/0", null), "explicit"));
        chains.set(index, new FieldChain(chain.target(), chain.field(), steps));
        assertThrows(Schema2Exception.class, () -> restore(expected, expected.projection().rules(), chains));
    }

    @Test void pointerLengthAndEscapesAreValidatedWithoutRecursiveRegexBacktracking() {
        var expected = ProductionSchema2Projection.compose(ProductionRuleArtifactTest.fixedArtifacts());
        for (String pointer : List.of("/" + "a".repeat(4095), "/" + "😀".repeat(4095),
                "/" + "~0~1".repeat(1023), "", "/a~0b~1c")) {
            var chains = withFirstPointer(expected, pointer);
            assertDoesNotThrow(() -> restore(expected, expected.projection().rules(), chains));
        }
        for (String pointer : List.of("/" + "a".repeat(4096), "/" + "😀".repeat(4096), "/a" + "😀".repeat(4095),
                "missing-slash", "/~", "/~2", "/bad\u0000", "/bad\u007f", "/bad\u0085", "/bad\u009f", "/bad\ud800")) {
            var chains = withFirstPointer(expected, pointer);
            assertThrows(Schema2Exception.class, () -> restore(expected, expected.projection().rules(), chains));
        }
    }

    private static List<FieldChain> withFirstPointer(ProductionSchema2Projection expected, String pointer) {
        var chains = new ArrayList<>(expected.projection().chains()); var chain = chains.getFirst();
        var steps = new ArrayList<>(chain.steps()); var step = steps.getFirst(); var location = step.authorLocation();
        steps.set(0, new ChainStep(step.op(), step.before(), step.after(), step.provider(), step.loadIndex(),
                new Location(location.moduleKey(), location.releaseVersion(), location.rawZipSha256(), location.path(), pointer, null), step.origin()));
        chains.set(0, new FieldChain(chain.target(), chain.field(), steps)); return chains;
    }

    @Test void canonicalByteBudgetRejectsBoundedTypedRowsBeforeAllocatingCanonicalArrays() {
        String raw = "a".repeat(64), logical = "b".repeat(64), body = "😀".repeat(1000), name = "😀".repeat(120);
        String pointer = "/" + "a".repeat(4090);
        var members = java.util.stream.IntStream.range(0, 4).mapToObj(i -> new ProductionSchema2Projection.StoredMember(
                "module_" + i, "1", i == 0 ? Role.BASE : Role.EXTENSION, raw, logical)).toList();
        List<FinalRule> rules = new ArrayList<>(); List<FieldChain> chains = new ArrayList<>();
        for (int i = 0; i < 4; i++) for (int j = 0; j < 64; j++) {
            String module = "module_" + i; RuleIdentity identity = new RuleIdentity(module, "character.language", "language." + String.format("%03d", j));
            rules.add(new FinalLanguage(new LanguageDefinition(identity, name, body, Category.STANDARD, 59, body), true));
            for (Field field : List.of(Field.AUTHOR_NOTE, Field.CATEGORY, Field.DESCRIPTION, Field.DISPLAY_NAME, Field.ENABLED, Field.SOURCE_PAGE)) {
                FieldValue value = switch (field) {
                    case AUTHOR_NOTE, DESCRIPTION -> new TextValue(body);
                    case CATEGORY -> new TextValue("STANDARD");
                    case DISPLAY_NAME -> new TextValue(name);
                    case ENABLED -> new BooleanValue(true);
                    case SOURCE_PAGE -> new IntegerValue(59);
                    default -> throw new AssertionError();
                };
                chains.add(new FieldChain(identity, field, List.of(new ChainStep("initial", new FieldState(StateKind.UNINITIALIZED, null),
                        new FieldState(StateKind.PRESENT, value), new Provider(module, "1"), i,
                        new Location(module, "1", raw, "rules.json", pointer, null), field == Field.ENABLED ? "default_enabled" : "declared"))));
            }
        }
        assertEquals("S2_BUDGET", assertThrows(Schema2Exception.class,
                () -> ProductionSchema2Projection.restore(members, rules, List.of(), chains)).diagnostic().code());
    }

    @Test void storedOrderedMemberIdentityAndProductionDigestsAreValidated() {
        var expected = ProductionSchema2Projection.compose(ProductionRuleArtifactTest.fixedArtifacts());
        var reversed = new ArrayList<>(expected.members()); Collections.reverse(reversed);
        assertThrows(RuntimeException.class, () -> ProductionSchema2Projection.restore(reversed, expected.projection().rules(), expected.projection().entries(), expected.projection().chains()));
        var first = expected.members().getFirst(); var members = new ArrayList<>(expected.members());
        members.set(0, new ProductionSchema2Projection.StoredMember(first.moduleKey(), first.releaseVersion(), first.role(), "A".repeat(64), first.logicalSha256()));
        var bad = members;
        assertThrows(RuntimeException.class, () -> ProductionSchema2Projection.restore(bad, expected.projection().rules(), expected.projection().entries(), expected.projection().chains()));
    }
    private static ProductionSchema2Projection restore(ProductionSchema2Projection expected, List<FinalRule> rules, List<FieldChain> chains) {
        return ProductionSchema2Projection.restore(expected.members(), rules, expected.projection().entries(), chains);
    }
}
