package com.dndtool.offline.rules;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded protocol parser: no coercion, duplicate keys, null, boolean or floating point values. */
final class OfflineJson {
    private final String input;
    private int position, tokens;
    private OfflineJson(String input) { this.input = input; }
    static Object parse(byte[] input) {
        if (input.length > 65536) throw bad();
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(input)).toString();
            var parser = new OfflineJson(text);
            Object value = parser.value(1);
            parser.space();
            if (parser.position != text.length()) throw bad();
            return value;
        } catch (java.nio.charset.CharacterCodingException exception) { throw bad(); }
    }
    static Map<String, Object> object(Object value, String... fields) {
        if (!(value instanceof Map<?, ?> map) || !map.keySet().equals(Set.of(fields))) throw bad();
        @SuppressWarnings("unchecked") Map<String,Object> result = (Map<String,Object>) map;
        return result;
    }
    static String string(Map<String,Object> map, String key) {
        if (!(map.get(key) instanceof String text)) throw bad();
        return text;
    }
    static long number(Map<String,Object> map, String key) {
        if (!(map.get(key) instanceof Long number)) throw bad();
        return number;
    }
    static List<?> array(Object value) {
        if (!(value instanceof List<?> result)) throw bad();
        return result;
    }
    private Object value(int depth) {
        if (depth > 5 || ++tokens > 1024) throw bad();
        space(); char c = peek();
        if (c == '"') return string();
        if (c == '{') {
            position++; Map<String,Object> map = new LinkedHashMap<>();
            if (take('}')) return map;
            do {
                space(); if (++tokens > 1024 || map.size() >= 32 || peek() != '"') throw bad();
                String key = string(); if (map.containsKey(key) || !take(':')) throw bad();
                map.put(key, value(depth + 1)); if (take('}')) return map;
            } while (take(','));
            throw bad();
        }
        if (c == '[') {
            position++; List<Object> list = new ArrayList<>(); if (take(']')) return list;
            do {
                if (list.size() >= 32) throw bad();
                list.add(value(depth + 1)); if (take(']')) return list;
            } while (take(','));
            throw bad();
        }
        if (c < '0' || c > '9') throw bad();
        int start = position++;
        while (position < input.length() && input.charAt(position) >= '0' && input.charAt(position) <= '9') {
            if (c == '0' || ++position - start > 19) throw bad();
        }
        try { return Long.valueOf(input.substring(start, position)); }
        catch (NumberFormatException exception) { throw bad(); }
    }
    private String string() {
        position++; StringBuilder result = new StringBuilder();
        while (position < input.length()) {
            char c = input.charAt(position++);
            if (c == '"') { String text = result.toString(); scalars(text); return text; }
            if (c < 32) throw bad();
            if (c == '\\') {
                c = peek(); position++;
                c = switch (c) {
                    case '"','\\','/' -> c;
                    case 'b' -> '\b'; case 'f' -> '\f'; case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t';
                    case 'u' -> unicode(); default -> throw bad();
                };
            }
            if (result.length() >= 4096) throw bad(); result.append(c);
        }
        throw bad();
    }
    private char unicode() {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            char c = peek(); position++;
            int digit = c >= '0' && c <= '9' ? c-'0' : c >= 'a' && c <= 'f' ? c-'a'+10 : c >= 'A' && c <= 'F' ? c-'A'+10 : -1;
            if (digit < 0) throw bad(); value = value * 16 + digit;
        }
        return (char) value;
    }
    static void scalars(String value) {
        for (int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i>=value.length() || !Character.isLowSurrogate(value.charAt(i))) throw bad();
            } else if (Character.isLowSurrogate(c)) throw bad();
        }
    }
    static String quote(String text) {
        scalars(text); StringBuilder out=new StringBuilder("\"");
        for(char c:text.toCharArray()) {
            if(c=='"'||c=='\\') out.append('\\').append(c);
            else if(c<32) out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));
            else out.append(c);
        }
        return out.append('"').toString();
    }
    private char peek() { if(position>=input.length()) throw bad(); return input.charAt(position); }
    private boolean take(char c) { space(); if(position<input.length() && input.charAt(position)==c) {position++;return true;} return false; }
    private void space() { while(position<input.length() && " \r\n\t".indexOf(input.charAt(position))>=0) position++; }
    static IllegalArgumentException bad() { return new IllegalArgumentException("Invalid offline rules protocol material"); }
}
