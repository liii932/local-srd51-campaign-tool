package com.dndtool.service.storage;

import com.dndtool.service.Schema2CharacterHealthRules;
import java.util.*;

/** Portable facts. Local CAS, epochs and HTTP receipts deliberately have no fields here. */
public final class CampaignData {
    private CampaignData() { }
    public record CharacterFact(String id, String name, long maximumHp, long currentHp) { }
    public record EventFact(long sequence, String type, String characterId, long beforeHp, long afterHp,
                            long amount, long applied, String name, long maximumHp) { }
    public record Snapshot(String campaignId, String name, List<String> rules,
                           List<CharacterFact> characters, List<EventFact> history, CampaignDomainData.State domains) {
        public Snapshot(String campaignId, String name, List<String> rules, List<CharacterFact> characters, List<EventFact> history) {
            this(campaignId, name, rules, characters, history, null);
        }
        public Snapshot {
            rules = List.copyOf(rules); characters = List.copyOf(characters); history = List.copyOf(history);
        }
    }
    public record Document(String fileKey, Snapshot snapshot) { }

    public static Snapshot validate(Snapshot s) {
        CampaignJson.uuid(s.campaignId()); CampaignJson.name(s.name()); CampaignRules.validate(s.rules());
        if (s.domains() != null) return CampaignDomainValidation.validate(s);
        if (s.rules().contains(CampaignRules.RUNTIME)) throw CampaignJson.bad("Runtime campaign requires complete domain facts");
        if (s.characters().size() > 10000 || s.history().size() > 100000) throw CampaignJson.bad("Campaign fact budget exceeded");
        Map<String, CharacterFact> actual = new TreeMap<>();
        String previousId = "";
        for (CharacterFact c : s.characters()) {
            CampaignJson.uuid(c.id()); CampaignJson.name(c.name()); CampaignRules.hp(c.maximumHp(), c.currentHp());
            if (c.id().compareTo(previousId) <= 0 || actual.put(c.id(), c) != null) throw CampaignJson.bad("Characters must have unique sorted identities");
            previousId = c.id();
        }
        Map<String, CharacterFact> fromHistory = new TreeMap<>(); long sequence = 0;
        for (EventFact e : s.history()) {
            if (e.sequence() != ++sequence) throw CampaignJson.bad("History must be contiguous from event one");
            CampaignJson.uuid(e.characterId());
            if (e.type().equals("CHARACTER_CREATED")) {
                CampaignJson.name(e.name()); CampaignRules.hp(e.maximumHp(), e.afterHp());
                if (e.beforeHp() != e.afterHp() || e.amount() != 0 || e.applied() != 0 || fromHistory.putIfAbsent(e.characterId(), new CharacterFact(e.characterId(), e.name(), e.maximumHp(), e.afterHp())) != null) throw CampaignJson.bad("Invalid character creation history");
            } else if (e.type().equals("HEALED")) {
                CharacterFact before = fromHistory.get(e.characterId());
                if (!s.rules().contains(CampaignRules.HEALING) || before == null || !e.name().isEmpty() || e.maximumHp() != 0 || e.beforeHp() != before.currentHp() || e.amount() < 0 || e.amount() > 65535) throw CampaignJson.bad("Healing history has an unsupported or nonadjacent source");
                var result = Schema2CharacterHealthRules.heal(before.currentHp(), before.maximumHp(), e.amount());
                if (result.after() != e.afterHp() || result.applied() != e.applied()) throw CampaignJson.bad("Healing history arithmetic differs from the frozen algorithm");
                fromHistory.put(before.id(), new CharacterFact(before.id(), before.name(), before.maximumHp(), result.after()));
            } else throw CampaignJson.bad("Unsupported history event type");
        }
        if (!actual.equals(fromHistory)) throw CampaignJson.bad("Current facts do not equal the complete adjacent history");
        return s;
    }
}
