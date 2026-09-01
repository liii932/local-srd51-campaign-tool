package com.dndtool.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Parser for the frozen {@code level-one-profile-v1} canonical identifier grammar. */
public final class LevelOneRuleProfile {
    private static final Set<String> ABILITIES = Set.of(
            "strength", "dexterity", "constitution", "intelligence", "wisdom", "charisma");
    private static final List<String> SEGMENT_ORDER = List.of(
            "bonus", "bonus_choice", "hp", "language", "save", "skill",
            "start", "subrace", "tool");

    private final Map<String, Integer> abilityBonuses;
    private final Choice abilityBonusChoice;
    private final Set<String> languages;
    private final Choice languageChoice;
    private final Set<String> skills;
    private final Choice skillChoice;
    private final Set<String> saves;
    private final Set<String> tools;
    private final Choice toolChoice;
    private final Choice startingChoice;
    private final boolean subraceRequired;
    private final Integer hitPointBase;

    private LevelOneRuleProfile(Builder value) {
        abilityBonuses = Map.copyOf(value.abilityBonuses);
        abilityBonusChoice = value.abilityBonusChoice;
        languages = Set.copyOf(value.languages);
        languageChoice = value.languageChoice;
        skills = Set.copyOf(value.skills);
        skillChoice = value.skillChoice;
        saves = Set.copyOf(value.saves);
        tools = Set.copyOf(value.tools);
        toolChoice = value.toolChoice;
        startingChoice = value.startingChoice;
        subraceRequired = value.subraceRequired;
        hitPointBase = value.hitPointBase;
    }

    public static LevelOneRuleProfile parse(String value) {
        if (value == null || value.isEmpty() || value.length() > 1000 || !isAscii(value)) {
            throw invalid();
        }
        Builder result = new Builder();
        Set<String> uniqueSegments = new LinkedHashSet<>();
        int previousRank = -1;
        for (String segment : value.split("\\|", -1)) {
            int equals = segment.indexOf('=');
            if (equals <= 0 || equals == segment.length() - 1 || !uniqueSegments.add(segment)) {
                throw invalid();
            }
            String key = segment.substring(0, equals);
            String payload = segment.substring(equals + 1);
            int rank = SEGMENT_ORDER.indexOf(key);
            if (rank < previousRank) throw invalid();
            previousRank = rank;
            switch (key) {
                case "bonus" -> parseBonuses(payload, result);
                case "bonus_choice" -> {
                    if (result.abilityBonusChoice != null) throw invalid();
                    result.abilityBonusChoice = parseChoice(payload, ABILITIES);
                }
                case "language" -> parseGrantOrChoice(payload, result.languages,
                        choice -> {
                            if (result.languageChoice != null) throw invalid();
                            result.languageChoice = choice;
                        });
                case "skill" -> parseGrantOrChoice(payload, result.skills,
                        choice -> {
                            if (result.skillChoice != null) throw invalid();
                            result.skillChoice = choice;
                        });
                case "save" -> parseGrants(payload, result.saves);
                case "tool" -> parseGrantOrChoice(payload, result.tools,
                        choice -> {
                            if (result.toolChoice != null) throw invalid();
                            result.toolChoice = choice;
                        });
                case "start" -> {
                    if (result.startingChoice != null) throw invalid();
                    result.startingChoice = parseChoice(payload, null);
                }
                case "subrace" -> {
                    if (!"required".equals(payload) || result.subraceRequired) throw invalid();
                    result.subraceRequired = true;
                }
                case "hp" -> {
                    if (result.hitPointBase != null) throw invalid();
                    try {
                        result.hitPointBase = Integer.valueOf(payload);
                    } catch (NumberFormatException exception) {
                        throw invalid();
                    }
                    if (!Set.of(6, 8, 10, 12).contains(result.hitPointBase)) throw invalid();
                }
                default -> throw invalid();
            }
        }
        return new LevelOneRuleProfile(result);
    }

    private static void parseBonuses(String payload, Builder result) {
        if (!result.abilityBonuses.isEmpty()) throw invalid();
        String previous = null;
        for (String token : payload.split(",", -1)) {
            int plus = token.indexOf('+');
            if (plus <= 0 || plus == token.length() - 1) throw invalid();
            String ability = token.substring(0, plus);
            int bonus;
            try {
                bonus = Integer.parseInt(token.substring(plus + 1));
            } catch (NumberFormatException exception) {
                throw invalid();
            }
            if (!ABILITIES.contains(ability) || bonus < 1 || bonus > 2
                    || previous != null && previous.compareTo(ability) >= 0
                    || result.abilityBonuses.putIfAbsent(ability, bonus) != null) {
                throw invalid();
            }
            previous = ability;
        }
    }

    private static void parseGrantOrChoice(
            String payload, Set<String> grants, java.util.function.Consumer<Choice> choiceSetter) {
        if (payload.indexOf(':') > 0) {
            choiceSetter.accept(parseChoice(payload, null));
        } else {
            parseGrants(payload, grants);
        }
    }

    private static void parseGrants(String payload, Set<String> target) {
        if (!target.isEmpty()) throw invalid();
        for (String key : canonicalKeys(payload)) {
            if (!target.add(key)) throw invalid();
        }
    }

    private static Choice parseChoice(String payload, Set<String> allowed) {
        int colon = payload.indexOf(':');
        if (colon != 1 || !Character.isDigit(payload.charAt(0))) throw invalid();
        int count = payload.charAt(0) - '0';
        List<String> candidates = canonicalKeys(payload.substring(colon + 1));
        if (count < 1 || count > candidates.size()
                || allowed != null && !allowed.containsAll(candidates)) {
            throw invalid();
        }
        return new Choice(count, candidates);
    }

    private static List<String> canonicalKeys(String value) {
        List<String> keys = new ArrayList<>();
        String previous = null;
        for (String key : value.split(",", -1)) {
            if (!stableKey(key) || previous != null && previous.compareTo(key) >= 0) {
                throw invalid();
            }
            keys.add(key);
            previous = key;
        }
        if (keys.isEmpty()) throw invalid();
        return List.copyOf(keys);
    }

    private static boolean stableKey(String value) {
        return value.matches("[a-z][a-z0-9_]*(?:[.][a-z0-9_]+)*") && value.length() <= 128;
    }

    private static boolean isAscii(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character > 0x7f || Character.isISOControl(character)) return false;
        }
        return true;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid level-one rule profile");
    }

    public Map<String, Integer> abilityBonuses() { return abilityBonuses; }
    public Choice abilityBonusChoice() { return abilityBonusChoice; }
    public Set<String> languages() { return languages; }
    public Choice languageChoice() { return languageChoice; }
    public Set<String> skills() { return skills; }
    public Choice skillChoice() { return skillChoice; }
    public Set<String> saves() { return saves; }
    public Set<String> tools() { return tools; }
    public Choice toolChoice() { return toolChoice; }
    public Choice startingChoice() { return startingChoice; }
    public boolean subraceRequired() { return subraceRequired; }
    public Integer hitPointBase() { return hitPointBase; }

    public record Choice(int count, List<String> candidates) {
        public Choice {
            if (count < 1) throw invalid();
            candidates = List.copyOf(Objects.requireNonNull(candidates));
        }
    }

    private static final class Builder {
        private final Map<String, Integer> abilityBonuses = new LinkedHashMap<>();
        private Choice abilityBonusChoice;
        private final Set<String> languages = new LinkedHashSet<>();
        private Choice languageChoice;
        private final Set<String> skills = new LinkedHashSet<>();
        private Choice skillChoice;
        private final Set<String> saves = new LinkedHashSet<>();
        private final Set<String> tools = new LinkedHashSet<>();
        private Choice toolChoice;
        private Choice startingChoice;
        private boolean subraceRequired;
        private Integer hitPointBase;
    }
}
