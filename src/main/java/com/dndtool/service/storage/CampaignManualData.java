package com.dndtool.service.storage;

import java.text.Normalizer;
import java.util.*;

/** Current application-owned simplified DM rules; these cards never enter the schema-2 engine. */
public final class CampaignManualData {
    private CampaignManualData() { }
    public static final List<String> TIERS = List.of("NONE", "HALF", "FULL", "EXPERTISE");
    public static final List<String> SIDES = List.of("ALLY", "ENEMY", "NEUTRAL");
    public static final List<String> CLASSES = List.of("barbarian", "bard", "cleric", "druid", "fighter", "monk", "paladin", "ranger", "rogue", "sorcerer", "warlock", "wizard").stream().map(c -> "class." + c).toList();
    public static final List<String> ABILITIES = List.of("strength", "dexterity", "constitution", "intelligence", "wisdom", "charisma").stream().map(a -> "ability." + a).toList();
    private static final String[][] SKILL_ROWS = {{"acrobatics","dexterity","体操"},{"animal_handling","wisdom","驯兽"},{"arcana","intelligence","奥秘"},{"athletics","strength","运动"},{"deception","charisma","欺瞒"},{"history","intelligence","历史"},{"insight","wisdom","洞悉"},{"intimidation","charisma","威吓"},{"investigation","intelligence","调查"},{"medicine","wisdom","医药"},{"nature","intelligence","自然"},{"perception","wisdom","察觉"},{"performance","charisma","表演"},{"persuasion","charisma","游说"},{"religion","intelligence","宗教"},{"sleight_of_hand","dexterity","巧手"},{"stealth","dexterity","隐匿"},{"survival","wisdom","求生"}};
    private static final List<Proficiency> SKILLS = Arrays.stream(SKILL_ROWS).map(r -> new Proficiency("skill." + r[0], r[2], "ability." + r[1])).toList();
    private static final List<String> ABILITY_NAMES=List.of("力量","敏捷","体质","智力","感知","魅力");
    private static final List<String> CLASS_NAMES=List.of("野蛮人","吟游诗人","牧师","德鲁伊","战士","武僧","圣武士","游侠","游荡者","术士","邪术师","法师");
    private static final List<Proficiency> SAVES = java.util.stream.IntStream.range(0,ABILITIES.size()).mapToObj(i -> new Proficiency(ABILITIES.get(i).replace("ability.", "save."), ABILITY_NAMES.get(i)+"豁免", ABILITIES.get(i))).toList();
    private static final List<Field> FIELDS;
    private static final Catalog CATALOG;
    static {
        List<Field> fields = new ArrayList<>(); for (int i=0;i<ABILITIES.size();i++) fields.add(new Field(ABILITIES.get(i),ABILITY_NAMES.get(i),1,30,10));
        fields.addAll(List.of(new Field("hp.maximum", "最大HP", 1, 999, 1), new Field("hp.current", "当前HP", 0, 999, 1), new Field("armor_class", "AC", 0, 99, 10), new Field("speed.ground", "步行速度", 0, 999, 30)));
        FIELDS = List.copyOf(fields);
        CATALOG = new Catalog(FIELDS, java.util.stream.IntStream.range(0,CLASSES.size()).mapToObj(i->new Entry(CLASSES.get(i),CLASS_NAMES.get(i))).toList(), SKILLS, SAVES, TIERS,
                List.of(new Entry("blank", "空白"), new Entry("commoner", "平民"), new Entry("guard", "守卫"), new Entry("wolf", "狼")),
                List.of(new ItemTemplate("item.backpack", "背包", "用于携带物品的普通背包"), new ItemTemplate("item.rope_hempen_50ft", "50 英尺麻绳", "普通麻绳"), new ItemTemplate("item.torch", "火把", "普通照明用火把")),
                List.of("NORMAL", "ADVANTAGE", "DISADVANTAGE"), List.of("MANUAL", "ABILITY", "SKILL", "SAVING_THROW"),
                List.of("HP", "GRANT_MODULE_ITEM", "GRANT_TEMPORARY_ITEM", "POSITION", "MESSAGE"), SIDES);
    }
    public record Field(String key, String name, int minimum, int maximum, int defaultValue) { }
    public record Entry(String key, String name) { }
    public record Proficiency(String key, String name, String abilityKey) { }
    public record ItemTemplate(String key, String name, String description) { }
    public record Catalog(List<Field> fields, List<Entry> classes, List<Proficiency> skills, List<Proficiency> saves,
                          List<String> tiers, List<Entry> templates, List<ItemTemplate> items, List<String> rollModes,
                          List<String> checkKinds, List<String> effectKinds, List<String> sides) { }
    public static Catalog catalog() { return CATALOG; }
    public record Card(UUID id, String name, String kind, boolean active, Map<String,Integer> fields,
                       Map<String,Integer> classes, Map<String,String> skills, Map<String,String> saves, long version) {
        public Card {
            uuid(id); CampaignJson.name(name);
            if (!Set.of("PC", "NPC").contains(kind) || version < 0 || version > 100000) throw CampaignJson.bad("Invalid manual card identity or version");
            fields = integerMap(fields); classes = integerMap(classes); skills = textMap(skills); saves = textMap(saves);
            if (!fields.keySet().equals(new HashSet<>(FIELDS.stream().map(Field::key).toList()))) throw CampaignJson.bad("Incomplete manual fields");
            for (Field field : FIELDS) if (fields.get(field.key()) < field.minimum() || fields.get(field.key()) > field.maximum()) throw CampaignJson.bad("Manual field outside bounds");
            if (fields.get("hp.current") > fields.get("hp.maximum") || !CLASSES.containsAll(classes.keySet())
                    || classes.values().stream().anyMatch(v -> v < 1 || v > 20) || classes.values().stream().mapToInt(Integer::intValue).sum() > 20) throw CampaignJson.bad("Manual class or HP bounds differ");
            if (!skills.keySet().equals(new HashSet<>(SKILLS.stream().map(Proficiency::key).toList())) || !saves.keySet().equals(new HashSet<>(SAVES.stream().map(Proficiency::key).toList()))
                    || !TIERS.containsAll(skills.values()) || !TIERS.containsAll(saves.values())) throw CampaignJson.bad("Incomplete manual proficiency tiers");
        }
        public int totalLevel() { return classes.values().stream().mapToInt(Integer::intValue).sum(); }
        public int proficiencyBonus() { return totalLevel() == 0 ? 2 : 2 + (totalLevel() - 1) / 4; }
    }
    public record ManualCharacter(Card initial, Card current) {
        public ManualCharacter { if (initial == null || current == null || !initial.id().equals(current.id()) || initial.version() != 0) throw CampaignJson.bad("Invalid manual initial/current identity"); }
        public UUID id() { return current.id(); }
    }
    public record Participant(UUID characterId, String side) { public Participant { uuid(characterId); if (!SIDES.contains(side)) throw CampaignJson.bad("Unknown encounter side"); } }
    public record Deployment(UUID characterId, String side, UUID nodeId) { public Deployment { uuid(characterId); uuid(nodeId); if (!SIDES.contains(side)) throw CampaignJson.bad("Unknown encounter side"); } }
    public record Encounter(UUID id, String status, List<Participant> participants) {
        public Encounter {
            uuid(id); participants = List.copyOf(participants);
            if (!Set.of("ACTIVE", "CLOSED").contains(status) || participants.isEmpty() || participants.size() > 10000
                    || new HashSet<>(participants.stream().map(Participant::characterId).toList()).size() != participants.size()) throw CampaignJson.bad("Invalid simple encounter");
        }
    }
    public record State(List<ManualCharacter> characters, List<Encounter> encounters, UUID partyNodeId) {
        public State {
            characters = List.copyOf(characters); encounters = List.copyOf(encounters); if (partyNodeId != null) uuid(partyNodeId);
            if (characters.size() > 10000 || encounters.size() > 10000 || new HashSet<>(characters.stream().map(ManualCharacter::id).toList()).size() != characters.size()
                    || new HashSet<>(encounters.stream().map(Encounter::id).toList()).size() != encounters.size() || encounters.stream().filter(e -> e.status().equals("ACTIVE")).count() > 1) throw CampaignJson.bad("Manual domain capacity or identity conflict");
        }
        public static State empty() { return new State(List.of(), List.of(), null); }
    }
    public static Card initial(UUID id, String name, String kind, String template) {
        Map<String,Integer> fields = new TreeMap<>(); FIELDS.forEach(f -> fields.put(f.key(), f.defaultValue()));
        if (!CATALOG.templates().stream().map(Entry::key).toList().contains(template) || !template.equals("blank") && !kind.equals("NPC")) throw CampaignJson.bad("Invalid NPC template selection");
        int[] values = switch (template) { case "commoner" -> new int[]{10,10,10,10,10,10,4,10,30}; case "guard" -> new int[]{13,12,12,10,11,10,11,16,30}; case "wolf" -> new int[]{12,15,12,3,12,6,11,13,40}; default -> null; };
        if (values != null) { for (int i = 0; i < 6; i++) fields.put(ABILITIES.get(i), values[i]); fields.put("hp.maximum", values[6]); fields.put("hp.current", values[6]); fields.put("armor_class", values[7]); fields.put("speed.ground", values[8]); }
        Map<String,String> skills = new TreeMap<>(), saves = new TreeMap<>(); SKILLS.forEach(p -> skills.put(p.key(), "NONE")); SAVES.forEach(p -> saves.put(p.key(), "NONE"));
        return new Card(id, name, kind, true, fields, Map.of(), skills, saves, 0);
    }
    public static boolean active(State state) { return state.encounters().stream().anyMatch(e -> e.status().equals("ACTIVE")); }
    public static State touch(State state,UUID id) {
        List<ManualCharacter> changed=new ArrayList<>();boolean found=false;
        for(ManualCharacter c:state.characters())if(c.id().equals(id)){
            Card v=c.current();if(!v.active())throw CampaignJson.bad("Inactive manual owner");
            changed.add(new ManualCharacter(c.initial(),new Card(v.id(),v.name(),v.kind(),v.active(),v.fields(),v.classes(),v.skills(),v.saves(),v.version()+1)));found=true;
        }else changed.add(c);
        return found?new State(changed,state.encounters(),state.partyNodeId()):state;
    }
    public static void uuid(UUID id) { if (id == null) throw CampaignJson.bad("Missing UUID"); CampaignJson.uuid(id.toString()); }
    public static String text(String value, int minimum, int maximum) {
        if (value == null || !Normalizer.isNormalized(value, Normalizer.Form.NFC) || value.codePointCount(0, value.length()) < minimum || value.codePointCount(0, value.length()) > maximum) throw CampaignJson.bad("Manual text outside bounds");
        CampaignJson.validString(value); return value;
    }
    private static Map<String,Integer> integerMap(Map<String,Integer> values) { if (values == null || values.entrySet().stream().anyMatch(e -> e.getKey() == null || e.getValue() == null)) throw CampaignJson.bad("Invalid manual integer map"); return Collections.unmodifiableMap(new TreeMap<>(values)); }
    private static Map<String,String> textMap(Map<String,String> values) { if (values == null || values.entrySet().stream().anyMatch(e -> e.getKey() == null || e.getValue() == null)) throw CampaignJson.bad("Invalid manual text map"); return Collections.unmodifiableMap(new TreeMap<>(values)); }
}
