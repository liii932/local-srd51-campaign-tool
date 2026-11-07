package com.dndtool.web;

import com.dndtool.service.storage.BundledCampaignRules;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;

/** Selects explicit bundled test members from the protected rule-library inventory. */
final class RuleHttpFixture {
    private RuleHttpFixture() { }

    static List<JsonObject> members(JsonObject library, String combination) {
        var candidates = library.getAsJsonArray("bundled").asList().stream().map(JsonElement::getAsJsonObject)
                .map(candidate -> candidate.getAsJsonObject("member")).toList();
        return BundledCampaignRules.artifacts(combination).stream().map(artifact -> {
            String expected = String.join("@", artifact.moduleKey(), artifact.releaseVersion(), artifact.rawZipSha256(),
                    artifact.logicalSha256(), artifact.role().name());
            return candidates.stream().filter(member -> tuple(member).equals(expected)).findFirst()
                    .orElseThrow(() -> new AssertionError("Missing exact bundled HTTP candidate: " + artifact.moduleKey()));
        }).toList();
    }

    static String tuple(JsonObject member) {
        return String.join("@", List.of("moduleKey", "releaseVersion", "rawZipSha256", "logicalSha256", "role").stream()
                .map(key -> member.get(key).getAsString()).toList());
    }
}
