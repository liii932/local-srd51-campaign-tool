package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class CampaignArchiveReaderTest {
    private static final String UNKNOWN_CHARACTER =
            "22222222-2222-4222-8222-222222222222";
    private final CampaignArchiveReader reader = new CampaignArchiveReader();

    @Test
    void readsCanonicalExportWithoutDependingOnObjectMemberOrder() {
        String json = validJson();
        String reordered = json.replace(
                "\"campaignKey\":\"" + CampaignArchiveExportServiceTest.CAMPAIGN_KEY
                        + "\",\"campaignName\":\"测试战役\",\"campaignStatus\":\"ACTIVE\"",
                "\"campaignStatus\":\"ACTIVE\",\"campaignName\":\"测试战役\","
                        + "\"campaignKey\":\""
                        + CampaignArchiveExportServiceTest.CAMPAIGN_KEY + "\"");

        CampaignArchiveReader.Result result = read(reordered);

        assertEquals(CampaignArchiveReader.Status.READY, result.status());
        assertEquals(1, result.document().formatVersion());
        assertEquals("测试战役", result.document().campaign().campaignName());
        assertEquals(4, result.document().fields().size());
        assertEquals(12L, result.document().fields().get(1).integerValue());
        assertEquals("12.500000000000000000",
                result.document().fields().get(2).decimalValue().toPlainString());
        assertEquals(true, result.document().fields().get(3).booleanValue());
    }

    @Test
    void rejectsEmptyOversizedMalformedUtf8AndBomBeforeParsing() {
        assertStatus(CampaignArchiveReader.Status.INVALID_DOCUMENT, new byte[0]);
        assertStatus(CampaignArchiveReader.Status.FILE_TOO_LARGE,
                new byte[CampaignSaveFileService.MAX_BYTES + 1]);
        assertStatus(CampaignArchiveReader.Status.INVALID_UTF8,
                new byte[] {(byte) 0xC3, (byte) 0x28});
        assertStatus(CampaignArchiveReader.Status.INVALID_UTF8,
                ("\uFEFF" + validJson()).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsNonRfcSyntaxTrailingContentAndExcessiveDepth() {
        assertStatus(CampaignArchiveReader.Status.INVALID_JSON,
                readBytes(validJson().replace("{\"formatVersion\"", "{/*x*/\"formatVersion\"")));
        assertStatus(CampaignArchiveReader.Status.INVALID_JSON,
                readBytes(validJson() + " null"));
        String deeplyNested = "{\"unknown\":" + "[".repeat(CampaignArchiveReader.MAX_DEPTH)
                + "0" + "]".repeat(CampaignArchiveReader.MAX_DEPTH) + "}";
        assertStatus(CampaignArchiveReader.Status.DEPTH_EXCEEDED, readBytes(deeplyNested));
    }

    @Test
    void rejectsDuplicateUnknownAndMissingMembers() {
        assertStatus(CampaignArchiveReader.Status.DUPLICATE_KEY, readBytes(validJson().replace(
                "\"formatVersion\":1,",
                "\"formatVersion\":1,\"formatVersion\":1,")));
        assertStatus(CampaignArchiveReader.Status.DUPLICATE_KEY, readBytes(validJson().replace(
                "\"campaignName\":\"测试战役\",",
                "\"campaignName\":\"测试战役\",\"campaignName\":\"重复\",")));
        assertStatus(CampaignArchiveReader.Status.INVALID_STRUCTURE, readBytes(validJson().replace(
                "\"formatVersion\":1,",
                "\"formatVersion\":1,\"unknown\":false,")));
        assertStatus(CampaignArchiveReader.Status.INVALID_STRUCTURE, readBytes(validJson().replace(
                "\"formatVersion\":1,", "")));
    }

    @Test
    void rejectsWrongJsonTypesAndNonIntegralIntegerTokens() {
        assertStatus(CampaignArchiveReader.Status.INVALID_STRUCTURE, readBytes(validJson().replace(
                "\"formatVersion\":1", "\"formatVersion\":\"1\"")));
        assertStatus(CampaignArchiveReader.Status.INVALID_VALUE, readBytes(validJson().replace(
                "\"level\":2", "\"level\":2.0")));
        assertStatus(CampaignArchiveReader.Status.INVALID_VALUE, readBytes(validJson().replace(
                "\"valueType\":\"INTEGER\",\"value\":12",
                "\"valueType\":\"INTEGER\",\"value\":\"12\"")));
        assertStatus(CampaignArchiveReader.Status.INVALID_STRUCTURE, readBytes(validJson().replace(
                "\"itemKey\":null", "\"itemKey\":false")));
    }

    @Test
    void rejectsUnpublishedReleaseAndMismatchedArchiveFormatWithoutFallback() {
        assertStatus(CampaignArchiveReader.Status.UNSUPPORTED_RELEASE,
                readBytes(validJson().replace(
                        "dnd5e2014_srd51_se_v1", "dnd5e2014_srd51_se")));
        assertStatus(CampaignArchiveReader.Status.UNSUPPORTED_FORMAT,
                readBytes(validJson().replace(
                        "\"formatVersion\":1", "\"formatVersion\":2")));
    }

    @Test
    void rejectsInvalidUnicodeNormalizationControlsAndTextBounds() {
        assertStatus(CampaignArchiveReader.Status.INVALID_VALUE, readBytes(validJson().replace(
                "\"campaignName\":\"测试战役\"",
                "\"campaignName\":\" " + "a".repeat(80) + " \"")));
        assertStatus(CampaignArchiveReader.Status.INVALID_VALUE, readBytes(validJson().replace(
                "\"campaignName\":\"测试战役\"",
                "\"campaignName\":\"e\\u0301\"")));
        assertStatus(CampaignArchiveReader.Status.INVALID_VALUE, readBytes(validJson().replace(
                "\"campaignName\":\"测试战役\"",
                "\"campaignName\":\"bad\\u0001name\"")));
        String unpairedSurrogateEscape = "\\" + "uD800";
        assertStatus(CampaignArchiveReader.Status.INVALID_VALUE, readBytes(validJson().replace(
                "\"campaignName\":\"测试战役\"",
                "\"campaignName\":\"" + unpairedSurrogateEscape + "\"")));
    }

    @Test
    void rejectsUnknownCharacterReferencesAndDuplicateRelations() {
        String fieldPrefix = "\"fields\":[{\"characterKey\":\""
                + CampaignArchiveExportServiceTest.CHARACTER_KEY;
        assertStatus(CampaignArchiveReader.Status.INVALID_RELATIONSHIP,
                readBytes(validJson().replace(fieldPrefix,
                        "\"fields\":[{\"characterKey\":\"" + UNKNOWN_CHARACTER)));

        String firstField = "{\"characterKey\":\""
                + CampaignArchiveExportServiceTest.CHARACTER_KEY
                + "\",\"fieldKey\":\"field.a_text\",\"valueType\":\"TEXT\","
                + "\"value\":\"备注\"}";
        assertStatus(CampaignArchiveReader.Status.INVALID_VALUE, readBytes(validJson().replace(
                "\"fields\":[" + firstField + ",",
                "\"fields\":[" + firstField + "," + firstField + ",")));
    }

    @Test
    void rejectsInvalidStableKeysTypedValuesAndCrossFieldRules() {
        assertStatus(CampaignArchiveReader.Status.INVALID_VALUE, readBytes(validJson().replace(
                "\"fieldKey\":\"field.a_text\"", "\"fieldKey\":\"Field A\"")));
        assertStatus(CampaignArchiveReader.Status.INVALID_VALUE, readBytes(validJson().replace(
                "12.500000000000000000", "123456789012345678901234567890123456789")));
        assertStatus(CampaignArchiveReader.Status.INVALID_VALUE, readBytes(validJson().replace(
                "\"sourceKind\":\"TEMPORARY\",\"itemKey\":null",
                "\"sourceKind\":\"TEMPORARY\",\"itemKey\":\"item.key\"")));
        assertStatus(CampaignArchiveReader.Status.INVALID_VALUE, readBytes(validJson().replace(
                "\"quantity\":2,\"itemStatus\":\"ACTIVE\"",
                "\"quantity\":1000,\"itemStatus\":\"ACTIVE\"")));
        assertStatus(CampaignArchiveReader.Status.INVALID_VALUE, readBytes(validJson().replace(
                "\"totalValue\":14,\"difficultyClass\":12,\"checkResult\":\"SUCCESS\"",
                "\"totalValue\":10,\"difficultyClass\":12,\"checkResult\":\"SUCCESS\"")));
    }

    @Test
    void rejectsMoreThanFiftyRecentEventsAndNonIncreasingSequence() {
        StringBuilder events = new StringBuilder();
        for (int index = 1; index <= 51; index++) {
            if (index > 1) events.append(',');
            events.append("{\"eventSequence\":").append(index)
                    .append(",\"eventType\":\"NOTE\",\"subjectCharacterKey\":null,")
                    .append("\"eventText\":\"note\",\"check\":null}");
        }
        assertStatus(CampaignArchiveReader.Status.INVALID_VALUE,
                readBytes(replaceEvents(validJson(), events.toString())));
        assertStatus(CampaignArchiveReader.Status.INVALID_VALUE,
                readBytes(validJson().replace("\"eventSequence\":9", "\"eventSequence\":8")));
    }

    private static String replaceEvents(String json, String events) {
        int start = json.indexOf("\"recentEvents\":[") + "\"recentEvents\":[".length();
        return json.substring(0, start) + events + "]}";
    }

    private static String validJson() {
        return CampaignArchiveJsonWriter.write(CampaignArchiveExportServiceTest.snapshot());
    }

    private CampaignArchiveReader.Result read(String json) {
        return reader.read(readBytes(json));
    }

    private static byte[] readBytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private void assertStatus(CampaignArchiveReader.Status expected, byte[] bytes) {
        CampaignArchiveReader.Result result = reader.read(bytes);
        assertEquals(expected, result.status());
        assertNull(result.document());
    }
}
