package com.dndtool.web;

/** Minimal JSON string encoder for host DTOs; it never serializes domain or JDBC objects. */
final class JsonSupport {
    private JsonSupport() {
    }

    static String quote(String value) {
        if (value == null) return "null";
        StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
        value.codePoints().forEach(codePoint -> {
            switch (codePoint) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (codePoint < 0x20 || codePoint == 0x2028 || codePoint == 0x2029) {
                        escaped.append("\\u%04x".formatted(codePoint));
                    } else {
                        escaped.appendCodePoint(codePoint);
                    }
                }
            }
        });
        return escaped.append('"').toString();
    }
}
