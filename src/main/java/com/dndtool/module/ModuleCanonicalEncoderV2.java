package com.dndtool.module;

import com.dndtool.persistence.ModuleCatalog;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** Encodes the extensible typed-directory projection using canonical format version 2. */
public final class ModuleCanonicalEncoderV2 implements ModuleCanonicalEncoder {
    public static final int FORMAT_VERSION = 2;
    private static final byte[] MAGIC =
            "DND_TOOL_SE_MODULE_CANONICAL".getBytes(StandardCharsets.US_ASCII);
    private static final int PARTITION_COUNT = 4;
    private static final byte TEXT_TAG = 0x01;
    private static final byte IDENTIFIER_TAG = 0x02;
    private static final byte INTEGER_TAG = 0x03;
    private static final byte DECIMAL_TAG = 0x04;
    private static final byte BOOLEAN_TAG = 0x05;

    @Override
    public int formatVersion() {
        return FORMAT_VERSION;
    }

    /**
     * Database IDs, timestamps, release status and a stored digest are outside this digest domain.
     * Every canonical-v2 business row is represented by one of the three typed directory sections.
     */
    @Override
    public byte[] encode(ModuleCatalog catalog) throws ModuleCanonicalException {
        ModuleCatalogValidatorV2.validate(catalog);
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

                Comparator<ModuleCatalog.CatalogDefinition> definitionOrder =
                        by(ModuleCatalog.CatalogDefinition::definitionType)
                                .thenComparing(by(ModuleCatalog.CatalogDefinition::definitionKey));
                writePartition(output, "catalog_definition", catalog.catalogDefinitions(),
                        definitionOrder,
                        (target, row) -> writeRecord(target,
                                identifier("definition_type", row.definitionType()),
                                identifier("definition_key", row.definitionKey()),
                                text("display_name", row.displayName()),
                                text("description", row.description()),
                                integer("sort_order", row.sortOrder())));

                Comparator<ModuleCatalog.CatalogAttribute> attributeOrder =
                        by(ModuleCatalog.CatalogAttribute::definitionType)
                                .thenComparing(by(ModuleCatalog.CatalogAttribute::definitionKey))
                                .thenComparing(by(ModuleCatalog.CatalogAttribute::attributeKey))
                                .thenComparingInt(ModuleCatalog.CatalogAttribute::attributeOrder);
                writePartition(output, "catalog_attribute", catalog.catalogAttributes(),
                        attributeOrder,
                        (target, row) -> writeRecord(target,
                                identifier("definition_type", row.definitionType()),
                                identifier("definition_key", row.definitionKey()),
                                identifier("attribute_key", row.attributeKey()),
                                integer("attribute_order", row.attributeOrder()),
                                identifier("value_type", row.valueType()),
                                required("value", row.value())));

                Comparator<ModuleCatalog.CatalogRelation> relationOrder =
                        by(ModuleCatalog.CatalogRelation::sourceType)
                                .thenComparing(by(ModuleCatalog.CatalogRelation::sourceKey))
                                .thenComparing(by(ModuleCatalog.CatalogRelation::relationType))
                                .thenComparingInt(ModuleCatalog.CatalogRelation::relationOrder)
                                .thenComparing(by(ModuleCatalog.CatalogRelation::targetType))
                                .thenComparing(by(ModuleCatalog.CatalogRelation::targetKey));
                writePartition(output, "catalog_relation", catalog.catalogRelations(),
                        relationOrder,
                        (target, row) -> writeRecord(target,
                                identifier("source_type", row.sourceType()),
                                identifier("source_key", row.sourceKey()),
                                identifier("relation_type", row.relationType()),
                                identifier("target_type", row.targetType()),
                                identifier("target_key", row.targetKey()),
                                integer("relation_order", row.relationOrder())));
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
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
            writeValue(output, field.value());
        }
    }

    private static void writeValue(DataOutputStream output, ModuleCatalog.ScalarValue value)
            throws IOException, ModuleCanonicalException {
        if (value instanceof ModuleCatalog.TextValue text) {
            writePayload(output, TEXT_TAG,
                    ModuleCanonicalEncoderV1.strictUtf8(
                            ModuleCanonicalEncoderV1.normalizeText(text.value())));
        } else if (value instanceof ModuleCatalog.IdentifierValue identifier) {
            writePayload(output, IDENTIFIER_TAG,
                    ModuleCanonicalEncoderV1.strictAscii(identifier.value(), false));
        } else if (value instanceof ModuleCatalog.IntegerValue integer) {
            writePayload(output, INTEGER_TAG,
                    Long.toString(integer.value()).getBytes(StandardCharsets.US_ASCII));
        } else if (value instanceof ModuleCatalog.DecimalValue decimal) {
            BigDecimal raw = decimal.value();
            String canonical = raw.signum() == 0
                    ? "0"
                    : raw.stripTrailingZeros().toPlainString();
            writePayload(output, DECIMAL_TAG, canonical.getBytes(StandardCharsets.US_ASCII));
        } else if (value instanceof ModuleCatalog.BooleanValue bool) {
            output.writeByte(BOOLEAN_TAG);
            writeU32(output, 1);
            output.writeByte(bool.value() ? 1 : 0);
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
        byte[] bytes = ModuleCanonicalEncoderV1.strictAscii(name, false);
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

    private static <T> Comparator<T> by(Function<T, String> key) {
        return (left, right) -> ModuleCanonicalEncoderV1.compareCanonicalUtf8(
                key.apply(left), key.apply(right));
    }

    private static Field text(String name, String value) {
        return required(name, new ModuleCatalog.TextValue(value));
    }

    private static Field identifier(String name, String value) {
        return required(name, new ModuleCatalog.IdentifierValue(value));
    }

    private static Field integer(String name, long value) {
        return required(name, new ModuleCatalog.IntegerValue(value));
    }

    private static Field required(String name, ModuleCatalog.ScalarValue value) {
        return new Field(name, value);
    }

    private record Field(String name, ModuleCatalog.ScalarValue value) {
    }

    @FunctionalInterface
    private interface RecordWriter<T> {
        void write(DataOutputStream output, T row)
                throws IOException, ModuleCanonicalException;
    }
}
