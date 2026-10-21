package com.dndtool.module.composition;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.CRC32;

/** Encoding helpers for the validation-only schema 2 digest domain. */
final class Schema2ValidationCodec {
    private static final byte[] DIGEST_PREFIX =
            "local-dnd/schema2-validation/1/".getBytes(StandardCharsets.UTF_8);
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Schema2ValidationCodec() {
    }

    static byte[] canonical(Object value) {
        StringBuilder json = new StringBuilder();
        appendValue(json, value);
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String digest(String tag, Object value) {
        MessageDigest digest = newSha256();
        digest.update(DIGEST_PREFIX);
        digest.update(Objects.requireNonNull(tag, "tag").getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        return HexFormat.of().formatHex(digest.digest(canonical(value)));
    }

    static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(newSha256().digest(bytes));
    }

    /** The caller supplies a bounded snapshot with validated ASCII file paths. */
    static byte[] rawZip(Map<String, byte[]> files) {
        if (files.size() > 0xffff) {
            throw new IllegalArgumentException("ZIP32 entry count exceeded");
        }
        List<String> paths = new ArrayList<>(files.keySet());
        paths.sort(String::compareTo);
        List<ZipMember> members = new ArrayList<>(paths.size());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (String path : paths) {
            byte[] name = path.getBytes(StandardCharsets.US_ASCII);
            if (name.length > 0xffff) {
                throw new IllegalArgumentException("ZIP32 file name length exceeded");
            }
            byte[] content = Objects.requireNonNull(files.get(path), "file content");
            CRC32 crc = new CRC32();
            crc.update(content);
            ZipMember member = new ZipMember(name, content.length, crc.getValue(), output.size());
            members.add(member);

            write32(output, 0x04034b50);
            write16(output, 20);
            write16(output, 0);
            write16(output, 0);
            write16(output, 0);
            write16(output, 33);
            write32(output, member.crc32());
            write32(output, member.size());
            write32(output, member.size());
            write16(output, name.length);
            write16(output, 0);
            output.writeBytes(name);
            output.writeBytes(content);
        }

        int centralOffset = output.size();
        for (ZipMember member : members) {
            write32(output, 0x02014b50);
            write16(output, 0x0314);
            write16(output, 20);
            write16(output, 0);
            write16(output, 0);
            write16(output, 0);
            write16(output, 33);
            write32(output, member.crc32());
            write32(output, member.size());
            write32(output, member.size());
            write16(output, member.name().length);
            write16(output, 0);
            write16(output, 0);
            write16(output, 0);
            write16(output, 0);
            write32(output, 0100644L << 16);
            write32(output, member.offset());
            output.writeBytes(member.name());
        }

        int centralSize = output.size() - centralOffset;
        write32(output, 0x06054b50);
        write16(output, 0);
        write16(output, 0);
        write16(output, members.size());
        write16(output, members.size());
        write32(output, centralSize);
        write32(output, centralOffset);
        write16(output, 0);
        return output.toByteArray();
    }

    private static void appendValue(StringBuilder json, Object value) {
        if (value instanceof Map<?, ?> object) {
            List<String> keys = new ArrayList<>(object.size());
            for (Object key : object.keySet()) {
                if (!(key instanceof String text) || !text.chars().allMatch(c -> c <= 0x7f)) {
                    throw new IllegalArgumentException("Canonical object keys must be ASCII strings");
                }
                keys.add(text);
            }
            keys.sort(String::compareTo);
            json.append('{');
            boolean first = true;
            for (String key : keys) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                appendString(json, key);
                json.append(':');
                appendValue(json, object.get(key));
            }
            json.append('}');
        } else if (value instanceof List<?> array) {
            json.append('[');
            boolean first = true;
            for (Object element : array) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                appendValue(json, element);
            }
            json.append(']');
        } else if (value instanceof String text) {
            appendString(json, text);
        } else if (value instanceof Integer || value instanceof Boolean) {
            json.append(value);
        } else {
            throw new IllegalArgumentException("Unsupported canonical value type");
        }
    }

    private static void appendString(StringBuilder json, String text) {
        json.append('"');
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            switch (value) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\t' -> json.append("\\t");
                case '\n' -> json.append("\\n");
                case '\f' -> json.append("\\f");
                case '\r' -> json.append("\\r");
                default -> {
                    if (value < 0x20) {
                        json.append("\\u00").append(HEX[value >>> 4]).append(HEX[value & 0xf]);
                    } else if (Character.isHighSurrogate(value)) {
                        if (index + 1 >= text.length()
                                || !Character.isLowSurrogate(text.charAt(index + 1))) {
                            throw new IllegalArgumentException("Canonical strings require Unicode scalars");
                        }
                        json.append(value).append(text.charAt(++index));
                    } else if (Character.isLowSurrogate(value)) {
                        throw new IllegalArgumentException("Canonical strings require Unicode scalars");
                    } else {
                        json.append(value);
                    }
                }
            }
        }
        json.append('"');
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void write16(ByteArrayOutputStream output, int value) {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
    }

    private static void write32(ByteArrayOutputStream output, long value) {
        output.write((int) (value & 0xff));
        output.write((int) ((value >>> 8) & 0xff));
        output.write((int) ((value >>> 16) & 0xff));
        output.write((int) ((value >>> 24) & 0xff));
    }

    private record ZipMember(byte[] name, int size, long crc32, int offset) {
    }
}
