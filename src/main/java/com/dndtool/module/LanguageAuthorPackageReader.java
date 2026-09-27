package com.dndtool.module;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure, bounded author-schema-1 reader. No filesystem, installation, approval or runtime loading. */
public final class LanguageAuthorPackageReader {
    public static final String HEADER_PATH = "author-package.json";
    public static final String LANGUAGE_PATH = "character/languages.json";
    public static final int MAX_HEADER_BYTES = 8192;
    public static final int MAX_LANGUAGE_BYTES = 262144;
    public static final int MAX_TOTAL_BYTES = MAX_HEADER_BYTES + MAX_LANGUAGE_BYTES;
    public static final int MAX_DEPTH = 4;
    public static final int MAX_TOKENS = 512;
    public static final int MAX_STRING_UNITS = 4096;

    public Result read(byte[] headerBytes, byte[] languageBytes) {
        // Check all allocations before copying or decoding either untrusted input.
        budget(headerBytes, MAX_HEADER_BYTES);
        budget(languageBytes, MAX_LANGUAGE_BYTES);
        if ((long) headerBytes.length + languageBytes.length > MAX_TOTAL_BYTES) throw invalid();
        byte[] headerSnapshot = headerBytes.clone();
        byte[] languageSnapshot = languageBytes.clone();
        Map<String, Object> head = object(parse(headerSnapshot), Set.of("author_schema_version",
                "module_key", "release_version", "package_display_name", "canonical_format_version",
                "archive_format_version", "hash_algorithm", "partitions"));
        int schema = integer(head, "author_schema_version");
        var identity = new BuiltinModuleReleaseRegistry.Identity(string(head, "module_key"),
                string(head, "release_version"));
        int canonical = integer(head, "canonical_format_version");
        int archive = integer(head, "archive_format_version");
        String algorithm = string(head, "hash_algorithm");
        if (schema != 1 || !identity.moduleKey().equals(BuiltinModuleReleaseRegistry.COMPLETE_MODULE_KEY)
                || !identity.releaseVersion().equals("1") || canonical != 2 || archive != 2
                || !algorithm.equals("SHA-256")) throw invalid();
        List<?> partitions = array(head.get("partitions"));
        if (partitions.size() != 1) throw invalid();
        Map<String, Object> declaration = object(partitions.getFirst(), Set.of("partition_key", "path"));
        if (!LanguagePartition.KEY.equals(string(declaration, "partition_key"))
                || !LANGUAGE_PATH.equals(string(declaration, "path"))) throw invalid();
        var header = new Header(identity, LanguagePartition.authorText(string(head, "package_display_name"), 120),
                schema, canonical, archive, algorithm);

        // A bare array has no redundant release header or caller-selected attribute bag.
        List<?> input = array(parse(languageSnapshot));
        if (input.size() != 18) throw invalid();
        List<LanguagePartition.Language> rows = new ArrayList<>(18);
        for (Object value : input) {
            Map<String, Object> row = object(value, Set.of("language_key", "display_name", "description",
                    "category", "source_page", "sort_order"));
            rows.add(new LanguagePartition.Language(string(row, "language_key"),
                    LanguagePartition.authorText(string(row, "display_name"), 120),
                    LanguagePartition.authorText(string(row, "description"), 1000),
                    category(string(row, "category")), integer(row, "source_page"), integer(row, "sort_order")));
        }
        return new Result(header, new LanguagePartition(rows), List.of(raw(HEADER_PATH, headerSnapshot),
                raw(LANGUAGE_PATH, languageSnapshot)));
    }

    public record Header(BuiltinModuleReleaseRegistry.Identity identity, String packageDisplayName,
                         int authorSchemaVersion, int canonicalFormatVersion, int archiveFormatVersion,
                         String hashAlgorithm) { }

    public record RawFile(String path, int byteLength, String rawSha256) { }

    /** Raw evidence covers exactly two supplied byte arrays, not a directory or installation manifest. */
    public record Result(Header header, LanguagePartition partition, List<RawFile> authorFiles) {
        public Result { authorFiles = List.copyOf(authorFiles); }
        public String verificationScope() { return "PARTITION"; }
    }

    private static void budget(byte[] bytes, int max) {
        if (bytes == null || bytes.length == 0 || bytes.length > max) throw invalid();
    }

    private static LanguagePartition.Category category(String value) {
        try { return LanguagePartition.Category.valueOf(value); }
        catch (IllegalArgumentException exception) { throw invalid(); }
    }

    private static RawFile raw(String path, byte[] bytes) {
        try {
            return new RawFile(path, bytes.length,
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static Object parse(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            return new Json(text).document();
        } catch (CharacterCodingException exception) { throw invalid(); }
    }

    private static Map<String, Object> object(Object value, Set<String> fields) {
        if (!(value instanceof Map<?, ?> map) || !map.keySet().equals(fields)) throw invalid();
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put((String) key, item));
        return result;
    }

    private static List<?> array(Object value) {
        if (!(value instanceof List<?> list)) throw invalid();
        return list;
    }

    private static String string(Map<String, Object> object, String key) {
        if (!(object.get(key) instanceof String value)) throw invalid();
        return value;
    }

    private static int integer(Map<String, Object> object, String key) {
        if (!(object.get(key) instanceof Integer value)) throw invalid();
        return value;
    }

    private static IllegalArgumentException invalid() { return LanguagePartition.invalid(); }

    /** Schema-specific strict JSON: only objects, arrays, strings and positive integer tokens exist. */
    private static final class Json {
        private final String text;
        private int position;
        private int tokens;
        Json(String text) { this.text = text; }

        Object document() {
            Object value = value(1);
            whitespace();
            if (position != text.length()) throw invalid();
            return value;
        }

        Object value(int depth) {
            if (depth > MAX_DEPTH || ++tokens > MAX_TOKENS) throw invalid();
            whitespace();
            char c = peek();
            if (c == '"') return string();
            if (c == '{') {
                position++;
                Map<String, Object> map = new LinkedHashMap<>();
                if (take('}')) return map;
                do {
                    whitespace();
                    if (++tokens > MAX_TOKENS || map.size() >= 8 || peek() != '"') throw invalid();
                    String key = string();
                    if (map.containsKey(key) || !take(':')) throw invalid();
                    map.put(key, value(depth + 1));
                    if (take('}')) return map;
                } while (take(','));
                throw invalid();
            }
            if (c == '[') {
                position++;
                List<Object> list = new ArrayList<>();
                if (take(']')) return list;
                do {
                    if (list.size() >= 18) throw invalid();
                    list.add(value(depth + 1));
                    if (take(']')) return list;
                } while (take(','));
                throw invalid();
            }
            if (c < '1' || c > '9') throw invalid();
            int start = position;
            while (position < text.length() && text.charAt(position) >= '0' && text.charAt(position) <= '9') {
                if (++position - start > 10) throw invalid();
            }
            try { return Integer.valueOf(text.substring(start, position)); }
            catch (NumberFormatException exception) { throw invalid(); }
        }

        String string() {
            position++; // Opening quote was checked by caller.
            StringBuilder value = new StringBuilder();
            while (position < text.length()) {
                char c = text.charAt(position++);
                if (c == '"') {
                    String decoded = value.toString();
                    LanguagePartition.requireScalars(decoded);
                    return decoded;
                }
                if (c < 0x20) throw invalid();
                if (c == '\\') {
                    if (position == text.length()) throw invalid();
                    c = text.charAt(position++);
                    c = switch (c) {
                        case '"', '\\', '/' -> c;
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case 'u' -> unicode();
                        default -> throw invalid();
                    };
                }
                if (value.length() >= MAX_STRING_UNITS) throw invalid();
                value.append(c);
            }
            throw invalid();
        }

        char unicode() {
            if (position + 4 > text.length()) throw invalid();
            int result = 0;
            for (int index = 0; index < 4; index++) {
                char c = text.charAt(position++);
                int digit = c >= '0' && c <= '9' ? c - '0'
                        : c >= 'a' && c <= 'f' ? c - 'a' + 10 : c >= 'A' && c <= 'F' ? c - 'A' + 10 : -1;
                if (digit < 0) throw invalid();
                result = result * 16 + digit;
            }
            return (char) result;
        }

        char peek() { if (position == text.length()) throw invalid(); return text.charAt(position); }
        boolean take(char wanted) {
            whitespace();
            if (position < text.length() && text.charAt(position) == wanted) { position++; return true; }
            return false;
        }
        void whitespace() {
            while (position < text.length()) {
                char c = text.charAt(position);
                if (c != ' ' && c != '\t' && c != '\r' && c != '\n') return;
                position++;
            }
        }
    }
}
