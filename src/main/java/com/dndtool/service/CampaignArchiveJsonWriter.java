package com.dndtool.service;

import com.dndtool.persistence.CampaignArchiveRepository.CheckSnapshot;
import com.dndtool.persistence.CampaignArchiveRepository.Encounter;
import com.dndtool.persistence.CampaignArchiveRepository.EventSnapshot;
import com.dndtool.persistence.CampaignArchiveRepository.FieldValue;
import com.dndtool.persistence.CampaignArchiveRepository.MapState;
import com.dndtool.persistence.CampaignArchiveRepository.Snapshot;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.StringWriter;

/** Writes the reviewed archive DTO explicitly; it never reflects over JDBC or domain objects. */
final class CampaignArchiveJsonWriter {
    private CampaignArchiveJsonWriter() {
    }

    static String write(Snapshot snapshot) {
        return write(snapshot, CampaignArchiveExportService.FORMAT_VERSION);
    }

    static String write(Snapshot snapshot, int formatVersion) {
        StringWriter output = new StringWriter();
        try (JsonWriter json = new JsonWriter(output)) {
            json.setHtmlSafe(false);
            json.setSerializeNulls(true);
            json.beginObject();
            json.name("formatVersion").value(formatVersion);
            writeCampaign(json, snapshot);
            writeModule(json, snapshot);
            writeCharacters(json, snapshot);
            writeFields(json, snapshot);
            writeClassLevels(json, snapshot);
            writeProficiencies(json, "skillProficiencies", snapshot.skillProficiencies());
            writeProficiencies(json, "saveProficiencies", snapshot.saveProficiencies());
            writeItems(json, snapshot);
            writeMaps(json, snapshot);
            writeEvents(json, snapshot);
            json.endObject();
        } catch (IOException exception) {
            // StringWriter does not perform external I/O; this indicates an internal writer bug.
            throw new IllegalStateException("Campaign archive JSON writing failed", exception);
        }
        return output.toString();
    }

    private static void writeCampaign(JsonWriter json, Snapshot snapshot) throws IOException {
        json.name("campaign").beginObject();
        json.name("campaignKey").value(snapshot.campaign().campaignKey());
        json.name("campaignName").value(snapshot.campaign().campaignName());
        json.name("campaignStatus").value(snapshot.campaign().campaignStatus());
        json.endObject();
    }

    private static void writeModule(JsonWriter json, Snapshot snapshot) throws IOException {
        json.name("module").beginObject();
        json.name("moduleKey").value(snapshot.module().frozenModuleKey());
        json.name("releaseVersion").value(snapshot.module().frozenReleaseVersion());
        json.name("contentSha256").value(snapshot.module().frozenContentSha256());
        json.endObject();
    }

    private static void writeCharacters(JsonWriter json, Snapshot snapshot) throws IOException {
        json.name("characters").beginArray();
        for (var character : snapshot.characters()) {
            json.beginObject();
            json.name("characterKey").value(character.characterKey());
            json.name("characterType").value(character.characterType());
            json.name("characterName").value(character.characterName());
            json.name("characterStatus").value(character.characterStatus());
            json.endObject();
        }
        json.endArray();
    }

    private static void writeFields(JsonWriter json, Snapshot snapshot) throws IOException {
        json.name("fields").beginArray();
        for (FieldValue field : snapshot.fields()) {
            json.beginObject();
            json.name("characterKey").value(field.characterKey());
            json.name("fieldKey").value(field.fieldKey());
            json.name("valueType").value(field.valueType());
            json.name("value");
            switch (field.valueType()) {
                case "TEXT" -> json.value(field.textValue());
                case "INTEGER" -> json.value(field.integerValue());
                case "DECIMAL" -> json.value(field.decimalValue());
                case "BOOLEAN" -> json.value(field.booleanValue());
                default -> throw new IllegalStateException("Unsupported archive field type");
            }
            json.endObject();
        }
        json.endArray();
    }

    private static void writeClassLevels(JsonWriter json, Snapshot snapshot) throws IOException {
        json.name("classLevels").beginArray();
        for (var classLevel : snapshot.classLevels()) {
            json.beginObject();
            json.name("characterKey").value(classLevel.characterKey());
            json.name("classKey").value(classLevel.classKey());
            json.name("level").value(classLevel.level());
            json.endObject();
        }
        json.endArray();
    }

    private static void writeProficiencies(
            JsonWriter json,
            String name,
            java.util.List<com.dndtool.persistence.CampaignArchiveRepository.Proficiency> values)
            throws IOException {
        json.name(name).beginArray();
        for (var proficiency : values) {
            json.beginObject();
            json.name("characterKey").value(proficiency.characterKey());
            json.name("targetKey").value(proficiency.targetKey());
            json.name("proficiencyKey").value(proficiency.proficiencyKey());
            json.endObject();
        }
        json.endArray();
    }

    private static void writeItems(JsonWriter json, Snapshot snapshot) throws IOException {
        json.name("items").beginArray();
        for (var item : snapshot.items()) {
            json.beginObject();
            json.name("characterKey").value(item.characterKey());
            json.name("sourceKind").value(item.sourceKind());
            json.name("itemKey").value(item.itemKey());
            json.name("itemName").value(item.itemName());
            json.name("itemDescription").value(item.itemDescription());
            json.name("quantity").value(item.quantity());
            json.name("itemStatus").value(item.itemStatus());
            json.endObject();
        }
        json.endArray();
    }

    private static void writeMaps(JsonWriter json, Snapshot snapshot) throws IOException {
        json.name("maps").beginArray();
        for (MapState map : snapshot.maps()) {
            json.beginObject();
            json.name("mapKey").value(map.mapKey());
            json.name("mapType").value(map.mapType());
            json.name("partyNodeKey").value(map.partyNodeKey());
            json.name("encounter");
            writeEncounter(json, map.encounter());
            json.endObject();
        }
        json.endArray();
    }

    private static void writeEncounter(JsonWriter json, Encounter encounter) throws IOException {
        if (encounter == null) {
            json.nullValue();
            return;
        }
        json.beginObject();
        json.name("battleStatus").value(encounter.battleStatus());
        json.name("participants").beginArray();
        for (var participant : encounter.participants()) {
            json.beginObject();
            json.name("characterKey").value(participant.characterKey());
            json.name("faction").value(participant.faction());
            json.name("nodeKey").value(participant.nodeKey());
            json.endObject();
        }
        json.endArray();
        json.endObject();
    }

    private static void writeEvents(JsonWriter json, Snapshot snapshot) throws IOException {
        json.name("recentEvents").beginArray();
        for (EventSnapshot event : snapshot.recentEvents()) {
            json.beginObject();
            json.name("eventSequence").value(event.eventSequence());
            json.name("eventType").value(event.eventType());
            json.name("subjectCharacterKey").value(event.subjectCharacterKey());
            json.name("eventText").value(event.eventText());
            json.name("check");
            writeCheck(json, event.check());
            json.endObject();
        }
        json.endArray();
    }

    private static void writeCheck(JsonWriter json, CheckSnapshot check) throws IOException {
        if (check == null) {
            json.nullValue();
            return;
        }
        json.beginObject();
        json.name("eventKey").value(check.eventKey());
        json.name("checkKey").value(check.checkKey());
        json.name("rollModeKey").value(check.rollModeKey());
        json.name("modifierSourceKey").value(check.modifierSourceKey());
        json.name("manualName").value(check.manualName());
        json.name("modifierValue").value(check.modifierValue());
        json.name("totalValue").value(check.totalValue());
        json.name("difficultyClass").value(check.difficultyClass());
        json.name("checkResult").value(check.checkResult());
        json.endObject();
    }
}
