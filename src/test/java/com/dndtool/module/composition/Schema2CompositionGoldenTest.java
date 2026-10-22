package com.dndtool.module.composition;

import com.google.gson.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.dndtool.module.composition.Schema2Fixtures.*;

class Schema2CompositionGoldenTest {
    @Test
    void exactAuthorBytesIndependentZipRecipeAndAllNinePublishedDigestsMatch() {
        var inputs=packages(); var authors=read(inputs); var expected=json(resource("expected/digests.json")).getAsJsonObject();
        for (int i=0;i<3;i++) {
            var author=authors.get(i); String module=MODULES.get(i);
            for (var file:inputs.get(i).entrySet()) {
                byte[] raw=file.getValue(); assertEquals('\n',raw[raw.length-1]);
                assertFalse(new String(raw,StandardCharsets.UTF_8).contains("\r"));
            }
            byte[] zip=storedZip(inputs.get(i));
            assertArrayEquals(zipResource(module),zip,"Independent ZIP32 recipe");
            assertArrayEquals(zip,author.rawZipBytes());
            assertEquals(expected.getAsJsonObject("raw").get(module).getAsString(),sha(zip));
            assertEquals(sha(zip),author.rawZipSha256());
            var logic=json(resource("expected/"+module+"-logic.json"));
            assertEquals(logic,json(author.logicalJson())); assertArrayEquals(canonical(logic),author.logicalJson());
            assertEquals(expected.getAsJsonObject("logic").get(module).getAsString(),digest("logic",logic));
            assertEquals(digest("logic",logic),author.logicalSha256());
        }
        var projection=KERNEL.compose(authors);
        assertEquals(json(resource("expected/configuration.json")),json(projection.configurationJson()));
        assertEquals(json(resource("expected/final-rules.json")),json(projection.finalRulesJson()));
        assertEquals(json(resource("expected/chains.json")),json(projection.chainsJson()));
        assertEquals(expected.get("config").getAsString(),projection.compositionSha256());
        assertEquals(expected.get("rules").getAsString(),projection.finalRulesSha256());
        assertEquals(expected.get("chain").getAsString(),projection.chainSha256());
        for (var pair:List.of(Map.entry("config",projection.configurationJson()),Map.entry("rules",projection.finalRulesJson()),Map.entry("chain",projection.chainsJson()))) {
            assertArrayEquals(canonical(json(pair.getValue())),pair.getValue());
            assertEquals(expected.get(pair.getKey()).getAsString(),digest(pair.getKey(),json(pair.getValue())));
        }
        assertEquals(4,projection.rules().size()); assertEquals(3,projection.entries().size()); assertEquals(31,projection.chains().size());
        var chains=json(projection.chainsJson()).getAsJsonObject().getAsJsonArray("chains");
        assertEquals(42,chains.asList().stream().mapToInt(c->c.getAsJsonObject().getAsJsonArray("steps").size()).sum());
        assertDoesNotThrow(()->KERNEL.verifyChains(authors,canonical(json(resource("expected/chains.json")))));
    }

    @Test
    void qualificationAlwaysRejectsThisIncompleteProjectionDespiteSelfConsistentDigests() {
        var projection=compose(packages());
        var failure=assertThrows(Schema2CompositionKernel.Schema2Exception.class,()->KERNEL.requireProductionQualification(projection));
        assertEquals("S2_BASE_INCOMPLETE",failure.diagnostic().code());
        assertEquals("QUALIFICATION",failure.diagnostic().stage().name());
    }

    @Test
    void snapshotsInputsAndReturnsImmutableModelsAndDefensiveByteArrays() {
        var inputs=packages(); var authors=read(inputs); var projection=KERNEL.compose(authors);
        byte[] finalBefore=projection.finalRulesJson(),chainsBefore=projection.chainsJson(),configBefore=projection.configurationJson();
        for(var files:inputs) { for(byte[] bytes:files.values())Arrays.fill(bytes,(byte)'!');files.clear(); }
        assertArrayEquals(finalBefore,KERNEL.compose(authors).finalRulesJson());
        var author=authors.getFirst();byte[] raw=author.rawZipBytes(),logic=author.logicalJson();Arrays.fill(raw,(byte)0);Arrays.fill(logic,(byte)0);
        assertEquals(json(resource("expected/"+BASE+"-logic.json")),json(author.logicalJson()));
        assertArrayEquals(zipResource(BASE),author.rawZipBytes());
        for(byte[] bytes:List.of(projection.finalRulesJson(),projection.chainsJson(),projection.configurationJson()))Arrays.fill(bytes,(byte)0);
        assertArrayEquals(finalBefore,projection.finalRulesJson());assertArrayEquals(chainsBefore,projection.chainsJson());assertArrayEquals(configBefore,projection.configurationJson());
        assertThrows(UnsupportedOperationException.class,()->projection.rules().clear());
        assertThrows(UnsupportedOperationException.class,()->projection.entries().clear());
        assertThrows(UnsupportedOperationException.class,()->projection.chains().clear());
        assertThrows(UnsupportedOperationException.class,()->projection.chains().getFirst().steps().clear());
        assertThrows(UnsupportedOperationException.class,()->authors.get(1).dependencies().clear());
        assertThrows(UnsupportedOperationException.class,()->authors.get(1).dependencies().getFirst().supportedVersions().clear());
        assertThrows(UnsupportedOperationException.class,()->authors.get(1).compatibleCombinations().getFirst().clear());
    }

    @Test
    void formattingAndFileSplittingPreserveLogicAndFinalRulesButBindNewRawEvidence() {
        var original=compose(packages()); var originals=read(packages());
        for(boolean split:List.of(false,true)) {
            var input=packages();
            if(split) {
                var definitions=json(input,0,"rules.json").getAsJsonArray(); var moved=new JsonArray();moved.add(definitions.remove(2));
                put(input,0,"rules.json",definitions);register(input,0,"second-rules.json","definitions",moved);
            } else {
                raw(input,0,"rules.json",new GsonBuilder().setPrettyPrinting().create().toJson(json(input,0,"rules.json"))+"\n\n");
                edit(input,0,"author-package.json",h->h.getAsJsonObject().addProperty("package_display_name","Another label"));
            }
            var changedAuthors=read(input);var changed=KERNEL.compose(changedAuthors);
            assertEquals(originals.getFirst().logicalSha256(),changedAuthors.getFirst().logicalSha256());
            assertNotEquals(originals.getFirst().rawZipSha256(),changedAuthors.getFirst().rawZipSha256());
            assertArrayEquals(original.finalRulesJson(),changed.finalRulesJson());
            assertNotEquals(original.compositionSha256(),changed.compositionSha256());
            assertNotEquals(original.chainSha256(),changed.chainSha256());
            assertDoesNotThrow(()->KERNEL.verifyChains(changedAuthors,changed.chainsJson()));
        }
    }

    @Test
    void deletingLegalSameValueInstructionChangesLogicAndChainsButPreservesFinalRules() {
        var original=compose(packages());var input=packages();edit(input,2,"changes.json",a->a.getAsJsonArray().remove(0));
        var authors=read(input);var changed=KERNEL.compose(authors);
        assertArrayEquals(original.finalRulesJson(),changed.finalRulesJson());assertEquals(original.finalRulesSha256(),changed.finalRulesSha256());
        assertNotEquals(read(packages()).get(2).logicalSha256(),authors.get(2).logicalSha256());
        assertNotEquals(original.compositionSha256(),changed.compositionSha256());assertNotEquals(original.chainSha256(),changed.chainSha256());
    }

    @Test
    void completeChainVerifierRejectsTamperedBeforeLocationProviderIndexOriginAndMissingSteps() {
        var authors=read(packages());var fixed=json(resource("expected/chains.json"));
        var chains=fixed.getAsJsonObject().getAsJsonArray("chains");
        int altered=0;for(int i=0;i<chains.size();i++)if(chains.get(i).getAsJsonObject().getAsJsonArray("steps").size()==3){altered=i;break;}
        final int index=altered;
        for(String mutation:List.of("before","location","provider","index","origin","drop","extra-chain")) {
            var tampered=fixed.deepCopy();var arr=tampered.getAsJsonObject().getAsJsonArray("chains");
            var steps=arr.get(index).getAsJsonObject().getAsJsonArray("steps");var step=steps.get(1).getAsJsonObject();
            switch(mutation) {
                case "before" -> step.add("before",JsonParser.parseString("{\"state\":\"absent\"}"));
                case "location" -> step.getAsJsonObject("author_location").addProperty("pointer","/999");
                case "provider" -> step.getAsJsonObject("provider").addProperty("release_version","01");
                case "index" -> step.addProperty("load_index",0);
                case "origin" -> step.addProperty("origin","declared");
                case "drop" -> steps.remove(1);
                case "extra-chain" -> arr.add(arr.get(0).deepCopy());
            }
            assertNotEquals(digest("chain",fixed),digest("chain",tampered));
            assertThrows(Schema2CompositionKernel.Schema2Exception.class,()->KERNEL.verifyChains(authors,canonical(tampered)),mutation);
        }
    }

    @Test
    void derivedChainsCanExceedAuthorArrayBudgetAndStillVerifyCompletely() {
        var input=packages();var definitions=json(input,0,"rules.json").getAsJsonArray();
        var template=definitions.get(0).getAsJsonObject();
        // 43 language definitions produce 258 chains by themselves; the set and relations add 10.
        for(int i=0;i<41;i++) {
            var extra=template.deepCopy();extra.getAsJsonObject("identity").addProperty("rule_key","language.extra"+i);
            extra.addProperty("display_name","Extra "+i);definitions.add(extra);
        }
        put(input,0,"rules.json",definitions);
        var authors=List.of(KERNEL.read(input.getFirst()));var projection=KERNEL.compose(authors);
        assertEquals(44,projection.rules().size());assertEquals(2,projection.entries().size());assertEquals(268,projection.chains().size());
        assertEquals(268,json(projection.chainsJson()).getAsJsonObject().getAsJsonArray("chains").size());
        var rule=json(projection.finalRulesJson()).getAsJsonObject().getAsJsonArray("rules").asList().stream()
                .map(JsonElement::getAsJsonObject).filter(r->r.getAsJsonObject("identity").get("rule_key").getAsString().equals("language.extra40")).findFirst().orElseThrow();
        assertEquals("Extra 40",rule.get("display_name").getAsString());assertTrue(rule.get("enabled").getAsBoolean());
        assertDoesNotThrow(()->KERNEL.verifyChains(authors,projection.chainsJson()));
    }

    private static byte[] zipResource(String module) { return HexFormat.of().parseHex(new String(resource(module+".zip.hex"),StandardCharsets.US_ASCII).replaceAll("\\s", "")); }

    /** Explicit standard little-endian ZIP32 headers, independent of the production ZIP builder. */
    private static byte[] storedZip(Map<String,byte[]> files) {
        var local=new ByteArrayOutputStream();var central=new ByteArrayOutputStream();
        for(var file:new TreeMap<>(files).entrySet()) {
            byte[] name=file.getKey().getBytes(StandardCharsets.US_ASCII),body=file.getValue();var crc=new CRC32();crc.update(body);int offset=local.size();
            u32(local,0x04034b50);u16(local,20);u16(local,0);u16(local,0);u16(local,0);u16(local,33);u32(local,crc.getValue());u32(local,body.length);u32(local,body.length);u16(local,name.length);u16(local,0);local.writeBytes(name);local.writeBytes(body);
            u32(central,0x02014b50);u16(central,0x0314);u16(central,20);u16(central,0);u16(central,0);u16(central,0);u16(central,33);u32(central,crc.getValue());u32(central,body.length);u32(central,body.length);u16(central,name.length);u16(central,0);u16(central,0);u16(central,0);u16(central,0);u32(central,0100644L<<16);u32(central,offset);central.writeBytes(name);
        }
        int offset=local.size();local.writeBytes(central.toByteArray());u32(local,0x06054b50);u16(local,0);u16(local,0);u16(local,files.size());u16(local,files.size());u32(local,central.size());u32(local,offset);u16(local,0);return local.toByteArray();
    }
    private static void u16(ByteArrayOutputStream b,long value){b.write((int)value&255);b.write((int)(value>>>8)&255);}
    private static void u32(ByteArrayOutputStream b,long value){u16(b,value);u16(b,value>>>16);}
}
