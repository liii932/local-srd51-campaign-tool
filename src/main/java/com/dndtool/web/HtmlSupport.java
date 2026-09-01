package com.dndtool.web;

/** Minimal HTML text/attribute encoder for server-rendered host-only JSP values. */
public final class HtmlSupport {
    private HtmlSupport() {
    }

    public static String escape(Object raw) {
        if (raw == null) return "";
        String value = raw.toString();
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        value.codePoints().forEach(codePoint -> {
            switch (codePoint) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.appendCodePoint(codePoint);
            }
        });
        return escaped.toString();
    }
}
