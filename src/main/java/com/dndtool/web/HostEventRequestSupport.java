package com.dndtool.web;

import com.dndtool.persistence.CharacterVersionRepository.VersionExpectation;
import com.dndtool.persistence.EncounterStateRepository.Faction;
import com.dndtool.security.HostRequestSecurityFilter;
import com.dndtool.service.CheckRequestPolicy.EffectRequest;
import com.dndtool.service.CheckRequestPolicy.IntegerValue;
import com.dndtool.service.CheckRequestPolicy.ParameterInput;
import com.dndtool.service.CheckRequestPolicy.ReferenceValue;
import com.dndtool.service.CheckRequestPolicy.TextValue;
import com.dndtool.service.EncounterStateService.ParticipantRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Parses the deliberately small form vocabulary exposed by the host-command host page. */
final class HostEventRequestSupport {
    private static final int MAX_EFFECTS_PER_BRANCH = 5;
    private static final int MAX_PARTICIPANTS = 100;

    private HostEventRequestSupport() {
    }

    static String attribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value instanceof String string ? string : null;
    }

    static long rowVersion(HttpServletRequest request) {
        return nonNegativeLong(attribute(
                request, HostRequestSecurityFilter.ROW_VERSION_REQUEST_ATTRIBUTE));
    }

    static int integer(String value) {
        if (value == null || !value.matches("-?(0|[1-9][0-9]*)")) {
            throw new IllegalArgumentException("integer required");
        }
        return Integer.parseInt(value);
    }

    static List<VersionExpectation> targetVersions(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<VersionExpectation> result = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (String line : text.split("\\R", -1)) {
            if (line.isBlank()) continue;
            String[] values = line.strip().split(",", -1);
            if (values.length != 2) throw new IllegalArgumentException("invalid target");
            String key = values[0].strip().toLowerCase(java.util.Locale.ROOT);
            if (!keys.add(key)) throw new IllegalArgumentException("duplicate target");
            result.add(new VersionExpectation(key, nonNegativeLong(values[1].strip())));
        }
        if (result.size() > MAX_PARTICIPANTS) {
            throw new IllegalArgumentException("too many targets");
        }
        return List.copyOf(result);
    }

    static List<ParticipantRequest> participants(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<ParticipantRequest> result = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (String line : text.split("\\R", -1)) {
            if (line.isBlank()) continue;
            String[] values = line.strip().split(",", -1);
            if (values.length != 3) throw new IllegalArgumentException("invalid participant");
            String key = values[0].strip().toLowerCase(java.util.Locale.ROOT);
            if (!keys.add(key)) throw new IllegalArgumentException("duplicate participant");
            result.add(new ParticipantRequest(
                    key, Faction.valueOf(values[1].strip().toUpperCase(java.util.Locale.ROOT)),
                    values[2].strip()));
        }
        if (result.size() > MAX_PARTICIPANTS) {
            throw new IllegalArgumentException("too many participants");
        }
        return List.copyOf(result);
    }

    static List<EffectRequest> effects(HttpServletRequest request, String branch) {
        String[] keys = request.getParameterValues(branch + "Effects");
        if (keys == null) return List.of();
        if (keys.length > MAX_EFFECTS_PER_BRANCH) {
            throw new IllegalArgumentException("too many effects");
        }
        List<EffectRequest> result = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (String key : keys) {
            if (key == null || !key.startsWith("effect.")) {
                throw new IllegalArgumentException("unknown effect");
            }
            if (!unique.add(key)) throw new IllegalArgumentException("duplicate effect");
            String prefix = branch + "." + key.substring("effect.".length()) + ".";
            result.add(effect(request, key, prefix));
        }
        return List.copyOf(result);
    }

    private static EffectRequest effect(
            HttpServletRequest request, String key, String prefix) {
        return switch (key) {
            case "effect.adjust_current_hp" -> new EffectRequest(key, List.of(
                    reference("target_character", request.getParameter(prefix + "target")),
                    integer("amount", request.getParameter(prefix + "amount"))));
            case "effect.grant_module_item" -> new EffectRequest(key, List.of(
                    reference("target_character", request.getParameter(prefix + "target")),
                    reference("item_template", request.getParameter(prefix + "item")),
                    integer("quantity", request.getParameter(prefix + "quantity"))));
            case "effect.grant_temporary_item" -> new EffectRequest(key, List.of(
                    reference("target_character", request.getParameter(prefix + "target")),
                    text("name", request.getParameter(prefix + "name")),
                    text("description", request.getParameter(prefix + "description")),
                    integer("quantity", request.getParameter(prefix + "quantity"))));
            case "effect.set_entity_position" -> new EffectRequest(key, List.of(
                    reference("target_character", request.getParameter(prefix + "target")),
                    reference("map", "map.tavern_cellar"),
                    reference("node", request.getParameter(prefix + "node"))));
            case "effect.append_event_message" -> new EffectRequest(key, List.of(
                    text("message", request.getParameter(prefix + "message"))));
            default -> throw new IllegalArgumentException("unknown effect");
        };
    }

    private static ParameterInput reference(String key, String value) {
        return new ParameterInput(key, new ReferenceValue(value));
    }

    private static ParameterInput integer(String key, String value) {
        return new ParameterInput(key, new IntegerValue(integer(value)));
    }

    private static ParameterInput text(String key, String value) {
        return new ParameterInput(key, new TextValue(value));
    }

    private static long nonNegativeLong(String value) {
        if (value == null || !value.matches("0|[1-9][0-9]*")) {
            throw new IllegalArgumentException("non-negative version required");
        }
        long parsed = Long.parseLong(value);
        if (parsed < 0) throw new IllegalArgumentException("negative version");
        return parsed;
    }
}
