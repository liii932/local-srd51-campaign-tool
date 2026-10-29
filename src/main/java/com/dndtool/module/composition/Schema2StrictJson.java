package com.dndtool.module.composition;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded JSON syntax reader. Schema interpretation belongs to the composition reader. */
final class Schema2StrictJson {
    // The complete monster stat_block is a 25-field atom; width stays bounded independently of tokens.
    private static final int MAX_OBJECT_FIELDS = 32;
    private final String text;
    private final Schema2CompositionKernel.Location source;
    private int position;
    private int tokens;

    private Schema2StrictJson(String text, Schema2CompositionKernel.Location source) {
        this.text = text;
        this.source = source;
    }

    static Object parse(byte[] bytes, Schema2CompositionKernel.Location source) {
        ByteBuffer input = ByteBuffer.wrap(bytes);
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(input).toString();
            return new Schema2StrictJson(text, source).document();
        } catch (CharacterCodingException error) {
            throw failure("S2_UTF8", "Malformed UTF-8", source.atByte(input.position()));
        }
    }

    private Object document() {
        Object result = value(1);
        whitespace();
        if (position != text.length()) throw syntax("Trailing JSON content");
        return result;
    }

    private Object value(int depth) {
        if (depth > 16 || ++tokens > 32768) throw budget();
        whitespace();
        char c = peek();
        if (c == '"') return string();
        if (c == '{') return object(depth);
        if (c == '[') return array(depth);
        if (literal("true")) return Boolean.TRUE;
        if (literal("false")) return Boolean.FALSE;
        if (literal("null")) return NullValue.INSTANCE;
        if (c == '-' || digit(c)) return number();
        throw syntax("Expected a JSON value");
    }

    private Map<String, Object> object(int depth) {
        position++;
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Integer> keyOffsets = new LinkedHashMap<>();
        if (take('}')) return result;
        while (true) {
            whitespace();
            if (++tokens > 32768 || result.size() >= MAX_OBJECT_FIELDS) throw budget();
            if (peek() != '"') throw syntax("Expected an object key");
            int start = position;
            String key = string();
            Integer previous = keyOffsets.putIfAbsent(key, start);
            if (previous != null) {
                throw new Schema2CompositionKernel.Schema2Exception(new Schema2CompositionKernel.Diagnostic(
                        "S2_DUPLICATE_KEY", Schema2CompositionKernel.Stage.PARSE, "Duplicate decoded JSON key",
                        List.of(at(previous), at(start))));
            }
            if (!take(':')) throw syntax("Expected ':'");
            result.put(key, value(depth + 1));
            if (take('}')) return result;
            if (!take(',')) throw syntax("Expected ',' or '}'");
        }
    }

    private List<Object> array(int depth) {
        position++;
        List<Object> result = new ArrayList<>();
        if (take(']')) return result;
        while (true) {
            if (result.size() >= 256) throw budget();
            result.add(value(depth + 1));
            if (take(']')) return result;
            if (!take(',')) throw syntax("Expected ',' or ']'");
        }
    }

    private String string() {
        position++;
        StringBuilder result = new StringBuilder();
        while (position < text.length()) {
            char c = text.charAt(position++);
            if (c == '"') {
                for (int i = 0; i < result.length(); i++) {
                    char item = result.charAt(i);
                    if (Character.isHighSurrogate(item)) {
                        if (++i >= result.length() || !Character.isLowSurrogate(result.charAt(i))) {
                            throw syntax("Isolated Unicode surrogate");
                        }
                    } else if (Character.isLowSurrogate(item)) throw syntax("Isolated Unicode surrogate");
                }
                return result.toString();
            }
            if (c < 0x20) throw syntax("Unescaped JSON control character");
            if (c == '\\') {
                if (position == text.length()) throw syntax("Incomplete escape");
                c = text.charAt(position++);
                c = switch (c) {
                    case '"', '\\', '/' -> c;
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case 'u' -> unicode();
                    default -> throw syntax("Invalid JSON escape");
                };
            }
            if (result.length() >= 4096) throw budget();
            result.append(c);
        }
        throw syntax("Unterminated JSON string");
    }

    private char unicode() {
        if (text.length() - position < 4) throw syntax("Incomplete Unicode escape");
        int value = 0;
        for (int i = 0; i < 4; i++) {
            char c = text.charAt(position++);
            int hex = c >= '0' && c <= '9' ? c - '0'
                    : c >= 'a' && c <= 'f' ? c - 'a' + 10
                    : c >= 'A' && c <= 'F' ? c - 'A' + 10 : -1;
            if (hex < 0) throw syntax("Invalid Unicode escape");
            value = (value << 4) | hex;
        }
        return (char) value;
    }

    private Object number() {
        int start = position;
        boolean integer = true;
        boolean negative = peek() == '-';
        if (negative) position++;
        if (peek() == '0') position++;
        else {
            if (peek() < '1' || peek() > '9') throw syntax("Invalid JSON number");
            while (digit(peek())) position++;
        }
        if (peek() == '.') {
            integer = false;
            position++;
            if (!digit(peek())) throw syntax("Invalid JSON fraction");
            while (digit(peek())) position++;
        }
        if (peek() == 'e' || peek() == 'E') {
            integer = false;
            position++;
            if (peek() == '+' || peek() == '-') position++;
            if (!digit(peek())) throw syntax("Invalid JSON exponent");
            while (digit(peek())) position++;
        }
        // Keep lexical integers exact; each typed field still owns its allowed signed range.
        // Negative zero has no distinct canonical integer representation and remains unsupported.
        if (integer && position - start <= (negative ? 11 : 10)
                && !(negative && position - start == 2 && text.charAt(start + 1) == '0')) {
            try { return Integer.valueOf(text.substring(start, position)); }
            catch (NumberFormatException ignored) { /* A valid JSON number, but not a schema integer. */ }
        }
        return UnsupportedNumber.INSTANCE;
    }

    private boolean literal(String value) {
        if (!text.startsWith(value, position)) return false;
        position += value.length();
        return true;
    }

    private boolean take(char c) {
        whitespace();
        if (peek() != c) return false;
        position++;
        return true;
    }

    private void whitespace() {
        while (position < text.length()) {
            char c = text.charAt(position);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') return;
            position++;
        }
    }

    private char peek() { return position < text.length() ? text.charAt(position) : '\0'; }
    private static boolean digit(char c) { return c >= '0' && c <= '9'; }
    private Schema2CompositionKernel.Location at(int offset) {
        return source.atByte(text.substring(0, offset).getBytes(StandardCharsets.UTF_8).length);
    }
    private Schema2CompositionKernel.Schema2Exception syntax(String message) {
        return failure("S2_JSON", message, at(position));
    }
    private Schema2CompositionKernel.Schema2Exception budget() {
        return failure("S2_BUDGET", "JSON budget exceeded", at(position));
    }
    private static Schema2CompositionKernel.Schema2Exception failure(String code, String message,
                                                                      Schema2CompositionKernel.Location location) {
        return new Schema2CompositionKernel.Schema2Exception(new Schema2CompositionKernel.Diagnostic(
                code, Schema2CompositionKernel.Stage.PARSE, message, List.of(location)));
    }

    enum NullValue { INSTANCE }
    enum UnsupportedNumber { INSTANCE }
}
