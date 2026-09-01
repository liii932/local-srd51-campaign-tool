package com.dndtool.module;

import com.dndtool.persistence.ModuleCatalog;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** Encodes one validated logical module projection using canonical format version 1. */
public final class ModuleCanonicalEncoderV1 implements ModuleCanonicalEncoder {
    public static final int FORMAT_VERSION = 1;

    @Override
    public int formatVersion() {
        return FORMAT_VERSION;
    }

    private static final byte[] MAGIC =
            "DND_TOOL_SE_MODULE_CANONICAL".getBytes(StandardCharsets.US_ASCII);
    private static final int PARTITION_COUNT = 23;

    private static final byte NULL_TAG = 0x00;
    private static final byte TEXT_TAG = 0x01;
    private static final byte IDENTIFIER_TAG = 0x02;
    private static final byte INTEGER_TAG = 0x03;
    private static final byte DECIMAL_TAG = 0x04;
    private static final byte BOOLEAN_TAG = 0x05;

    /**
     * Returns a new canonical byte array. Input list order, database IDs, timestamps, release
     * status, and a previously stored content digest do not affect the result.
     */
    @Override
    public byte[] encode(ModuleCatalog catalog) throws ModuleCanonicalException {
        ModuleCatalogValidatorV1.validate(catalog);

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.write(MAGIC);
                writeU32(output, FORMAT_VERSION);
                writeU32(output, PARTITION_COUNT);

                writePartition(output, "release", List.of(catalog.release()), null,
                        (target, row) -> writeRecord(target,
                                identifier("module_key", row.moduleKey()),
                                identifier("release_version", row.releaseVersion()),
                                integer("canonical_format_version", row.canonicalFormatVersion()),
                                identifier("hash_algorithm", row.hashAlgorithm())));

                writePartition(output, "rule_constant", catalog.ruleConstants(),
                        by(ModuleCatalog.RuleConstant::constantKey),
                        (target, row) -> writeRecord(target,
                                identifier("constant_key", row.constantKey()),
                                identifier("value_type", row.valueType()),
                                required("value", row.value())));

                writePartition(output, "field_definition", catalog.fieldDefinitions(),
                        by(ModuleCatalog.FieldDefinition::fieldKey),
                        (target, row) -> writeRecord(target,
                                identifier("field_key", row.fieldKey()),
                                text("display_name", row.displayName()),
                                identifier("data_type", row.dataType()),
                                required("default_value", row.defaultValue()),
                                nullable("minimum_value", row.minimumValue()),
                                nullable("maximum_value", row.maximumValue()),
                                nullableIdentifier(
                                        "dependent_max_field_key", row.dependentMaxFieldKey()),
                                nullableText("unit", row.unit()),
                                text("description", row.description())));

                writePartition(output, "class_definition", catalog.classDefinitions(),
                        by(ModuleCatalog.ClassDefinition::classKey),
                        (target, row) -> writeRecord(target,
                                identifier("class_key", row.classKey()),
                                text("display_name", row.displayName())));

                writePartition(output, "proficiency_tier", catalog.proficiencyTiers(),
                        by(ModuleCatalog.ProficiencyTier::proficiencyKey),
                        (target, row) -> writeRecord(target,
                                identifier("proficiency_key", row.proficiencyKey()),
                                identifier("enum_code", row.enumCode()),
                                integer("numerator", row.numerator()),
                                integer("denominator", row.denominator()),
                                identifier("rounding_algorithm", row.roundingAlgorithm())));

                Comparator<ModuleCatalog.ProficiencyBonusBand> bonusOrder =
                        Comparator.comparingInt(
                                        ModuleCatalog.ProficiencyBonusBand::minimumTotalLevel)
                                .thenComparingInt(
                                        ModuleCatalog.ProficiencyBonusBand::maximumTotalLevel)
                                .thenComparingInt(ModuleCatalog.ProficiencyBonusBand::bonus);
                writePartition(output, "proficiency_bonus_band",
                        catalog.proficiencyBonusBands(), bonusOrder,
                        (target, row) -> writeRecord(target,
                                integer("minimum_total_level", row.minimumTotalLevel()),
                                integer("maximum_total_level", row.maximumTotalLevel()),
                                integer("bonus", row.bonus())));

                writePartition(output, "skill_definition", catalog.skillDefinitions(),
                        by(ModuleCatalog.SkillDefinition::skillKey),
                        (target, row) -> writeRecord(target,
                                identifier("skill_key", row.skillKey()),
                                text("display_name", row.displayName()),
                                identifier("ability_field_key", row.abilityFieldKey())));

                writePartition(output, "save_definition", catalog.saveDefinitions(),
                        by(ModuleCatalog.SaveDefinition::saveKey),
                        (target, row) -> writeRecord(target,
                                identifier("save_key", row.saveKey()),
                                identifier("ability_field_key", row.abilityFieldKey())));

                writePartition(output, "item_template", catalog.itemTemplates(),
                        by(ModuleCatalog.ItemTemplate::itemKey),
                        (target, row) -> writeRecord(target,
                                identifier("item_key", row.itemKey()),
                                text("display_name", row.displayName()),
                                text("description", row.description())));

                writePartition(output, "npc_template", catalog.entityTemplates(),
                        by(ModuleCatalog.EntityTemplate::templateKey),
                        (target, row) -> writeRecord(target,
                                identifier("template_key", row.templateKey()),
                                text("display_name", row.displayName())));

                Comparator<ModuleCatalog.EntityTemplateValue> templateValueOrder =
                        by(ModuleCatalog.EntityTemplateValue::templateKey)
                                .thenComparing(by(ModuleCatalog.EntityTemplateValue::fieldKey));
                writePartition(output, "npc_template_field_value",
                        catalog.entityTemplateValues(), templateValueOrder,
                        (target, row) -> writeRecord(target,
                                identifier("template_key", row.templateKey()),
                                identifier("field_key", row.fieldKey()),
                                required("value", row.value())));

                Comparator<ModuleCatalog.EntityTemplateClassLevel> templateClassOrder =
                        by(ModuleCatalog.EntityTemplateClassLevel::templateKey)
                                .thenComparing(by(
                                        ModuleCatalog.EntityTemplateClassLevel::classKey));
                writePartition(output, "npc_template_class_level",
                        catalog.entityTemplateClassLevels(), templateClassOrder,
                        (target, row) -> writeRecord(target,
                                identifier("template_key", row.templateKey()),
                                identifier("class_key", row.classKey()),
                                integer("level", row.level())));

                Comparator<ModuleCatalog.EntityTemplateProficiency> templateProficiencyOrder =
                        by(ModuleCatalog.EntityTemplateProficiency::templateKey)
                                .thenComparing(by(
                                        ModuleCatalog.EntityTemplateProficiency::targetKind))
                                .thenComparing(by(
                                        ModuleCatalog.EntityTemplateProficiency::targetKey));
                writePartition(output, "npc_template_proficiency",
                        catalog.entityTemplateProficiencies(), templateProficiencyOrder,
                        (target, row) -> writeRecord(target,
                                identifier("template_key", row.templateKey()),
                                identifier("target_kind", row.targetKind()),
                                identifier("target_key", row.targetKey()),
                                identifier("proficiency_key", row.proficiencyKey())));

                writePartition(output, "check_definition", catalog.checkDefinitions(),
                        by(ModuleCatalog.CheckDefinition::checkKey),
                        (target, row) -> writeRecord(target,
                                identifier("check_key", row.checkKey()),
                                identifier("enum_code", row.enumCode()),
                                identifier("modifier_algorithm", row.modifierAlgorithm())));

                writePartition(output, "roll_mode", catalog.rollModes(),
                        by(ModuleCatalog.RollMode::rollModeKey),
                        (target, row) -> writeRecord(target,
                                identifier("roll_mode_key", row.rollModeKey()),
                                identifier("enum_code", row.enumCode()),
                                integer("candidate_count", row.candidateCount()),
                                identifier("selection_algorithm", row.selectionAlgorithm())));

                writePartition(output, "event_template", catalog.eventTemplates(),
                        by(ModuleCatalog.EventTemplate::eventKey),
                        (target, row) -> writeRecord(target,
                                identifier("event_key", row.eventKey()),
                                text("display_name", row.displayName())));

                Comparator<ModuleCatalog.EventCheck> eventCheckOrder =
                        by(ModuleCatalog.EventCheck::eventKey)
                                .thenComparing(by(ModuleCatalog.EventCheck::checkKey));
                writePartition(output, "event_allowed_check", catalog.eventChecks(),
                        eventCheckOrder,
                        (target, row) -> writeRecord(target,
                                identifier("event_key", row.eventKey()),
                                identifier("check_key", row.checkKey())));

                Comparator<ModuleCatalog.EventEffect> eventEffectOrder =
                        by(ModuleCatalog.EventEffect::eventKey)
                                .thenComparing(by(ModuleCatalog.EventEffect::effectKey));
                writePartition(output, "event_allowed_effect", catalog.eventEffects(),
                        eventEffectOrder,
                        (target, row) -> writeRecord(target,
                                identifier("event_key", row.eventKey()),
                                identifier("effect_key", row.effectKey())));

                writePartition(output, "effect_definition", catalog.effectDefinitions(),
                        by(ModuleCatalog.EffectDefinition::effectKey),
                        (target, row) -> writeRecord(target,
                                identifier("effect_key", row.effectKey()),
                                identifier("execution_algorithm", row.executionAlgorithm())));

                Comparator<ModuleCatalog.EffectParameter> parameterOrder =
                        by(ModuleCatalog.EffectParameter::effectKey)
                                .thenComparingInt(ModuleCatalog.EffectParameter::parameterOrder)
                                .thenComparing(by(ModuleCatalog.EffectParameter::parameterKey));
                writePartition(output, "effect_parameter", catalog.effectParameters(),
                        parameterOrder,
                        (target, row) -> writeRecord(target,
                                identifier("effect_key", row.effectKey()),
                                identifier("parameter_key", row.parameterKey()),
                                identifier("data_type", row.dataType()),
                                nullableIdentifier("reference_kind", row.referenceKind()),
                                nullable("minimum_value", row.minimumValue()),
                                nullable("maximum_value", row.maximumValue()),
                                nullableIdentifier(
                                        "text_normalization", row.textNormalization()),
                                nullableBoolean(
                                        "reject_control_characters",
                                        row.rejectControlCharacters()),
                                integer("parameter_order", row.parameterOrder())));

                writePartition(output, "map_definition", catalog.mapDefinitions(),
                        by(ModuleCatalog.MapDefinition::mapKey),
                        (target, row) -> writeRecord(target,
                                identifier("map_key", row.mapKey()),
                                identifier("map_type", row.mapType())));

                Comparator<ModuleCatalog.MapNode> mapNodeOrder =
                        by(ModuleCatalog.MapNode::mapKey)
                                .thenComparing(by(ModuleCatalog.MapNode::nodeKey));
                writePartition(output, "map_node", catalog.mapNodes(), mapNodeOrder,
                        (target, row) -> writeRecord(target,
                                identifier("map_key", row.mapKey()),
                                identifier("node_key", row.nodeKey()),
                                text("display_name", row.displayName())));

                Comparator<ModuleCatalog.MapConnection> connectionOrder =
                        by(ModuleCatalog.MapConnection::mapKey)
                                .thenComparing(by(
                                        ModuleCatalog.MapConnection::endpointLowKey))
                                .thenComparing(by(
                                        ModuleCatalog.MapConnection::endpointHighKey));
                writePartition(output, "map_connection", catalog.mapConnections(),
                        connectionOrder,
                        (target, row) -> writeRecord(target,
                                identifier("map_key", row.mapKey()),
                                identifier("endpoint_low_key", row.endpointLowKey()),
                                identifier("endpoint_high_key", row.endpointHighKey())));
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            // ByteArrayOutputStream does not perform external I/O.
            throw new AssertionError(impossible);
        }
    }

    private static <T> void writePartition(
            DataOutputStream output,
            String name,
            List<T> source,
            Comparator<T> order,
            RecordWriter<T> writer) throws IOException, ModuleCanonicalException {
        List<T> rows = new ArrayList<>(source);
        if (order != null) {
            rows.sort(order);
            for (int index = 1; index < rows.size(); index++) {
                if (order.compare(rows.get(index - 1), rows.get(index)) == 0) {
                    throw new ModuleCanonicalException();
                }
            }
        }

        writeName(output, name);
        writeU32(output, rows.size());
        for (T row : rows) {
            writer.write(output, row);
        }
    }

    private static void writeRecord(DataOutputStream output, Field... fields)
            throws IOException, ModuleCanonicalException {
        writeU32(output, fields.length);
        for (Field field : fields) {
            writeName(output, field.name());
            writeValue(output, field.value(), field.nullable());
        }
    }

    private static void writeValue(
            DataOutputStream output,
            ModuleCatalog.ScalarValue value,
            boolean nullable) throws IOException, ModuleCanonicalException {
        if (value == null) {
            if (!nullable) {
                throw new ModuleCanonicalException();
            }
            output.writeByte(NULL_TAG);
            return;
        }

        if (value instanceof ModuleCatalog.TextValue textValue) {
            writePayload(output, TEXT_TAG, strictUtf8(normalizeText(textValue.value())));
        } else if (value instanceof ModuleCatalog.IdentifierValue identifierValue) {
            writePayload(output, IDENTIFIER_TAG, strictAscii(identifierValue.value(), false));
        } else if (value instanceof ModuleCatalog.IntegerValue integerValue) {
            writePayload(output, INTEGER_TAG,
                    Long.toString(integerValue.value()).getBytes(StandardCharsets.US_ASCII));
        } else if (value instanceof ModuleCatalog.DecimalValue decimalValue) {
            BigDecimal decimal = decimalValue.value();
            String canonical = decimal.signum() == 0
                    ? "0"
                    : decimal.stripTrailingZeros().toPlainString();
            writePayload(output, DECIMAL_TAG, canonical.getBytes(StandardCharsets.US_ASCII));
        } else if (value instanceof ModuleCatalog.BooleanValue booleanValue) {
            output.writeByte(BOOLEAN_TAG);
            writeU32(output, 1);
            output.writeByte(booleanValue.value() ? 1 : 0);
        } else {
            throw new ModuleCanonicalException();
        }
    }

    private static void writePayload(DataOutputStream output, byte tag, byte[] payload)
            throws IOException, ModuleCanonicalException {
        if (payload.length > Integer.MAX_VALUE) {
            throw new ModuleCanonicalException();
        }
        output.writeByte(tag);
        writeU32(output, payload.length);
        output.write(payload);
    }

    private static void writeName(DataOutputStream output, String name)
            throws IOException, ModuleCanonicalException {
        byte[] bytes = strictAscii(name, false);
        writeU32(output, bytes.length);
        output.write(bytes);
    }

    private static void writeU32(DataOutputStream output, int value)
            throws IOException, ModuleCanonicalException {
        if (value < 0) {
            throw new ModuleCanonicalException();
        }
        output.writeInt(value);
    }

    static String normalizeText(String value) throws ModuleCanonicalException {
        if (value == null) {
            throw new ModuleCanonicalException();
        }
        String normalizedLines = value.replace("\r\n", "\n").replace('\r', '\n');
        String normalized = Normalizer.normalize(normalizedLines, Normalizer.Form.NFC);
        // Force strict validation now; StandardCharsets.UTF_8#getBytes would replace bad input.
        strictUtf8(normalized);
        return normalized;
    }

    static byte[] strictUtf8(String value) throws ModuleCanonicalException {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException exception) {
            throw new ModuleCanonicalException();
        }
    }

    static byte[] strictAscii(String value, boolean allowEmpty)
            throws ModuleCanonicalException {
        if (value == null || (!allowEmpty && value.isEmpty())) {
            throw new ModuleCanonicalException();
        }
        byte[] bytes = new byte[value.length()];
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character > 0x7f) {
                throw new ModuleCanonicalException();
            }
            bytes[index] = (byte) character;
        }
        return bytes;
    }

    /** Unsigned lexicographic comparison of normalized UTF-8, independent of locale. */
    static int compareCanonicalUtf8(String left, String right) {
        try {
            byte[] leftBytes = strictUtf8(normalizeText(left));
            byte[] rightBytes = strictUtf8(normalizeText(right));
            int shared = Math.min(leftBytes.length, rightBytes.length);
            for (int index = 0; index < shared; index++) {
                int comparison = Integer.compare(
                        Byte.toUnsignedInt(leftBytes[index]),
                        Byte.toUnsignedInt(rightBytes[index]));
                if (comparison != 0) {
                    return comparison;
                }
            }
            return Integer.compare(leftBytes.length, rightBytes.length);
        } catch (ModuleCanonicalException exception) {
            throw new IllegalArgumentException("Invalid canonical sort key", exception);
        }
    }

    private static <T> Comparator<T> by(Function<T, String> key) {
        return (left, right) -> compareCanonicalUtf8(key.apply(left), key.apply(right));
    }

    private static Field text(String name, String value) {
        return required(name, value == null ? null : new ModuleCatalog.TextValue(value));
    }

    private static Field identifier(String name, String value) {
        return required(name, value == null ? null : new ModuleCatalog.IdentifierValue(value));
    }

    private static Field integer(String name, long value) {
        return required(name, new ModuleCatalog.IntegerValue(value));
    }

    private static Field nullableText(String name, String value) {
        return nullable(name, value == null ? null : new ModuleCatalog.TextValue(value));
    }

    private static Field nullableIdentifier(String name, String value) {
        return nullable(name, value == null ? null : new ModuleCatalog.IdentifierValue(value));
    }

    private static Field nullableBoolean(String name, Boolean value) {
        return nullable(name, value == null ? null : new ModuleCatalog.BooleanValue(value));
    }

    private static Field required(String name, ModuleCatalog.ScalarValue value) {
        return new Field(name, value, false);
    }

    private static Field nullable(String name, ModuleCatalog.ScalarValue value) {
        return new Field(name, value, true);
    }

    private record Field(String name, ModuleCatalog.ScalarValue value, boolean nullable) {
    }

    @FunctionalInterface
    private interface RecordWriter<T> {
        void write(DataOutputStream output, T row)
                throws IOException, ModuleCanonicalException;
    }
}
