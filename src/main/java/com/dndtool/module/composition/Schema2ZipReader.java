package com.dndtool.module.composition;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** A deliberately finite ZIP32 profile. It never writes files or trusts a platform ZIP extractor. */
final class Schema2ZipReader {
    static final int MAX_ZIP_BYTES = 2 * 1024 * 1024;
    static final int MAX_METADATA_BYTES = 32768;
    static final int MAX_ENTRIES = MAX_AUTHOR_FILES + 4;
    static final int MAX_PATH_BYTES = 240;
    static final int MAX_EXPANDED_BYTES = MAX_PACKAGE_BYTES + 3 * MAX_METADATA_BYTES;
    static final Set<String> METADATA = Set.of("LICENSE", "NOTICE", "README.md");

    /** Constructor is private: callers cannot assert a raw ZIP/file-set association. */
    static final class DecodedZip {
        private final byte[] originalZip;
        private final Map<String, byte[]> authorFiles;
        private final Map<String, byte[]> originalMembers;
        private DecodedZip(byte[] originalZip, Map<String, byte[]> authorFiles, Map<String, byte[]> originalMembers) {
            this.originalZip = originalZip;
            this.authorFiles = Collections.unmodifiableMap(authorFiles);
            this.originalMembers = Collections.unmodifiableMap(originalMembers);
        }
        byte[] originalZip() { return originalZip; }
        Map<String, byte[]> authorFiles() { return authorFiles; }
        Map<String, byte[]> originalMembers() { return originalMembers; }
    }

    private record Member(String path, int needed, int flags, int method, int time, int date, long crc,
                          int compressed, int expanded, int localOffset, byte[] name, int dataOffset,
                          int localEnd) {
        Member withLocal(int dataOffset, int localEnd) {
            return new Member(path, needed, flags, method, time, date, crc, compressed, expanded,
                    localOffset, name, dataOffset, localEnd);
        }
    }

    private final byte[] zip;
    private final String sha;

    private Schema2ZipReader(byte[] zip) {
        if (zip.length > MAX_ZIP_BYTES) throw new Schema2Exception(new Diagnostic("S2_BUDGET", Stage.PARSE,
                "Original ZIP byte budget exceeded", List.of(new Location(null, null, null, "$zip", null, 0))));
        this.zip = zip.clone();
        this.sha = Schema2ValidationCodec.sha256(this.zip);
    }

    static DecodedZip read(byte[] zip) {
        if (zip == null) throw new Schema2Exception(new Diagnostic("S2_ZIP", Stage.PARSE,
                "Missing original ZIP bytes", List.of(new Location(null, null, null, "$zip", null, 0))));
        return new Schema2ZipReader(zip).decode();
    }

    private DecodedZip decode() {
        // No archive comment: EOCD is exactly the final 22 bytes. No scanner ambiguity or suffix.
        int eocd = zip.length - 22;
        signature(eocd, 0x06054b50L);
        int count = u16(eocd + 10);
        if (u16(eocd + 4) != 0 || u16(eocd + 6) != 0 || u16(eocd + 8) != count
                || count == 0xffff || u16(eocd + 20) != 0) {
            throw fail("S2_ZIP", "Split ZIP, ZIP64 or archive comments are unsupported", "$zip", eocd);
        }
        if (count == 0 || count > MAX_ENTRIES) throw fail("S2_BUDGET", "ZIP entry count budget exceeded", "$zip", eocd);
        long centralSize = u32(eocd + 12), centralOffset = u32(eocd + 16);
        if (centralOffset == 0xffffffffL || centralSize == 0xffffffffL
                || centralOffset + centralSize != eocd) {
            throw fail("S2_ZIP", "ZIP central directory is not contiguous with EOCD", "$zip", eocd);
        }
        int cursor = checkedInt(centralOffset);
        List<Member> members = new ArrayList<>(count);
        Set<String> paths = new HashSet<>();
        long expandedTotal = 0;
        long authorTotal = 0;
        for (int index = 0; index < count; index++) {
            int at = cursor;
            signature(at, 0x02014b50L);
            bounds(at, 46);
            int nameLength = u16(at + 28), extraLength = u16(at + 30), commentLength = u16(at + 32);
            bounds(at, 46L + nameLength + extraLength + commentLength);
            if (nameLength == 0 || nameLength > MAX_PATH_BYTES) throw fail("S2_BUDGET", "ZIP path byte budget exceeded", "$zip", at);
            byte[] name = Arrays.copyOfRange(zip, at + 46, at + 46 + nameLength);
            String path = path(name, at + 46);
            if (!paths.add(path.toLowerCase(Locale.ROOT))) throw fail("S2_ZIP", "Duplicate or case-colliding ZIP path", path, at);
            int flags = u16(at + 8), method = u16(at + 10);
            int madeBy = u16(at + 4), needed = u16(at + 6);
            long external = u32(at + 38), compressed = u32(at + 20), expanded = u32(at + 24), offset = u32(at + 42);
            int unixType = (int) (external >>> 16) & 0170000;
            if (((madeBy >>> 8) != 0 && (madeBy >>> 8) != 3) || needed < 10 || needed > 20
                    || (method == 8 && needed != 20)
                    || (flags & ~0x0800) != 0 || (method != 0 && method != 8)
                    || u16(at + 34) != 0 || u16(at + 36) != 0 || (external & 0xffff) != 0
                    || (unixType != 0 && unixType != 0100000)
                    || extraLength != 0 || commentLength != 0
                    || compressed == 0xffffffffL || expanded == 0xffffffffL || offset == 0xffffffffL) {
                throw fail("S2_ZIP", "Unsupported ZIP flags, entry type, extra fields or ZIP64", path, at);
            }
            int maximum = HEADER_PATH.equals(path) ? MAX_HEADER_BYTES : METADATA.contains(path) ? MAX_METADATA_BYTES : MAX_FILE_BYTES;
            if (expanded > maximum || expanded > compressed * 100L + 1024L) {
                throw fail("S2_BUDGET", "ZIP expansion budget exceeded", path, at);
            }
            expandedTotal += expanded;
            if (!METADATA.contains(path)) authorTotal += expanded;
            if (expandedTotal > MAX_EXPANDED_BYTES || authorTotal > MAX_PACKAGE_BYTES) {
                throw fail("S2_BUDGET", "ZIP package expansion budget exceeded", path, at);
            }
            if (method == 0 && compressed != expanded) throw fail("S2_ZIP", "STORED sizes differ", path, at);
            members.add(new Member(path, needed, flags, method, u16(at + 12), u16(at + 14), u32(at + 16),
                    checkedInt(compressed), (int) expanded, checkedInt(offset), name, 0, 0));
            cursor = at + 46 + nameLength;
        }
        if (cursor != eocd) throw fail("S2_ZIP", "Central directory count or size mismatch", "$zip", cursor);

        // Validate every local record and the whole layout before allocating expanded entry arrays.
        List<Member> local = new ArrayList<>(members.size());
        for (Member member : members) {
            int at = member.localOffset();
            signature(at, 0x04034b50L);
            bounds(at, 30);
            int nameLength = u16(at + 26), extraLength = u16(at + 28);
            long start = (long) at + 30 + nameLength + extraLength, end = start + member.compressed();
            bounds(at, end - at);
            if (u16(at + 4) != member.needed() || u16(at + 6) != member.flags()
                    || u16(at + 8) != member.method() || u16(at + 10) != member.time()
                    || u16(at + 12) != member.date() || u32(at + 14) != member.crc()
                    || u32(at + 18) != member.compressed() || u32(at + 22) != member.expanded()
                    || nameLength != member.name().length || extraLength != 0
                    || !Arrays.equals(zip, at + 30, at + 30 + nameLength, member.name(), 0, nameLength)
                    || end > centralOffset) {
                throw fail("S2_ZIP", "Local and central ZIP records disagree", member.path(), at);
            }
            local.add(member.withLocal(checkedInt(start), checkedInt(end)));
        }
        local.sort(Comparator.comparingInt(Member::localOffset));
        int end = 0;
        for (Member member : local) {
            if (member.localOffset() != end) throw fail("S2_ZIP", "ZIP overlap, prefix or unexplained gap", member.path(), member.localOffset());
            end = member.localEnd();
        }
        if (end != centralOffset) throw fail("S2_ZIP", "ZIP local records do not end at central directory", "$zip", end);
        Map<String, byte[]> author = new TreeMap<>();
        Map<String, byte[]> originalMembers = new TreeMap<>();
        for (Member member : local) {
            byte[] body = expand(member);
            if (METADATA.contains(member.path())) metadata(body, member);
            else author.put(member.path(), body);
            originalMembers.put(member.path(), body);
        }
        return new DecodedZip(zip, author, originalMembers);
    }

    private byte[] expand(Member member) {
        byte[] body = new byte[member.expanded()];
        if (member.method() == 0) System.arraycopy(zip, member.dataOffset(), body, 0, body.length);
        else {
            Inflater inflater = new Inflater(true);
            try {
                inflater.setInput(zip, member.dataOffset(), member.compressed());
                int written = 0;
                byte[] overflow = new byte[1];
                while (!inflater.finished()) {
                    int count = written < body.length ? inflater.inflate(body, written, body.length - written) : inflater.inflate(overflow);
                    if (written == body.length && count != 0) throw fail("S2_ZIP", "DEFLATE exceeds declared size", member.path(), member.dataOffset());
                    written += count;
                    if (count == 0 && !inflater.finished()) throw fail("S2_ZIP", "Truncated or stalled DEFLATE stream", member.path(), member.dataOffset());
                }
                if (written != body.length || inflater.getRemaining() != 0) throw fail("S2_ZIP", "DEFLATE length or trailing data mismatch", member.path(), member.dataOffset());
            } catch (DataFormatException failure) {
                throw fail("S2_ZIP", "Malformed DEFLATE stream", member.path(), member.dataOffset());
            } finally { inflater.end(); }
        }
        CRC32 crc = new CRC32();
        crc.update(body);
        if (crc.getValue() != member.crc()) throw fail("S2_ZIP", "ZIP CRC32 mismatch", member.path(), member.dataOffset());
        return body;
    }

    private void metadata(byte[] body, Member member) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body)).toString();
            if (text.codePoints().anyMatch(cp -> cp == 0xfeff || (cp < 32 && cp != 9 && cp != 10 && cp != 13) || cp >= 127 && cp <= 159)) {
                throw fail("S2_ZIP", "Metadata contains forbidden control characters or BOM", member.path(), member.dataOffset());
            }
        } catch (CharacterCodingException failure) {
            throw fail("S2_UTF8", "Metadata must be UTF-8", member.path(), member.dataOffset());
        }
    }

    private String path(byte[] bytes, int offset) {
        for (byte value : bytes) if (value < 0x21 || value > 0x7e) throw fail("S2_ZIP", "ZIP paths must be printable ASCII without spaces", "$zip", offset);
        String path = new String(bytes, StandardCharsets.US_ASCII);
        if (path.indexOf('\\') >= 0 || path.indexOf(':') >= 0 || path.startsWith("/") || path.endsWith("/")) {
            throw fail("S2_ZIP", "Unsafe ZIP path", path, offset);
        }
        for (String segment : path.split("/", -1)) {
            if (!segment.matches("[A-Za-z0-9][A-Za-z0-9._-]*") || segment.equals(".") || segment.equals("..") || segment.endsWith(".")) {
                throw fail("S2_ZIP", "Unsafe ZIP path segment", path, offset);
            }
        }
        return path;
    }

    private void signature(int at, long expected) {
        if (u32(at) != expected) throw fail("S2_ZIP", "Invalid ZIP record signature", "$zip", Math.max(0, at));
    }
    private int u16(int at) { bounds(at, 2); return (zip[at] & 255) | (zip[at + 1] & 255) << 8; }
    private long u32(int at) { return Integer.toUnsignedLong(u16(at) | u16(at + 2) << 16); }
    private int checkedInt(long value) {
        if (value < 0 || value > zip.length) throw fail("S2_ZIP", "ZIP offset or size is outside original bytes", "$zip", 0);
        return (int) value;
    }
    private void bounds(int at, long length) {
        if (at < 0 || length < 0 || at + length > zip.length) throw fail("S2_ZIP", "Truncated ZIP record", "$zip", Math.max(0, at));
    }
    private Schema2Exception fail(String code, String message, String path, int offset) {
        return new Schema2Exception(new Diagnostic(code, Stage.PARSE, message,
                List.of(new Location(null, null, sha, path, null, offset))));
    }
}
