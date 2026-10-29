package com.dndtool.module.composition;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.dndtool.module.composition.Schema2Fixtures.*;

class Schema2CompositionParsingTest {
    @Test
    void rejectsMalformedSyntaxAndRawUtf8InEveryAuthorFileWithoutPublishingAnObject() {
        for(String path:List.of("author-package.json","rules.json","members.json")) {
            for(String malformed:List.of("","/* comment */[]","[]{}","[1,]","{\"x\":1,}","{'x':1}","{x:1}","NaN","[+1]","[01]","[1e]","\ufeff[]")) {
                var p=packages();raw(p,0,path,malformed);rejected("S2_JSON","PARSE",p);
            }
            for(byte[] bytes:List.of(new byte[]{(byte)0xc0,(byte)0xaf},new byte[]{(byte)0xed,(byte)0xa0,(byte)0x80},new byte[]{(byte)0xe2,(byte)0x82},new byte[]{(byte)0xff})) {
                var p=packages();p.getFirst().put(path,bytes);var d=rejected("S2_UTF8","PARSE",p);
                assertEquals(path,d.locations().getFirst().path());assertEquals(0,d.locations().getFirst().byteOffset());
            }
        }
        for(String escaped:List.of("\\ud800","\\udfff","\\ud800x","\\ud800\\ud800")) {
            var p=packages();String text=new String(p.getFirst().get("rules.json"),StandardCharsets.UTF_8);raw(p,0,"rules.json",text.replace("\"Common\"","\""+escaped+"\""));rejected("S2_JSON","PARSE",p);
        }
    }

    @Test
    void integersUseExactTokensAndRangesAndBooleansNeverCoerce() {
        for(String token:List.of("\"59\"","59.0","5.9e1","-59","-0","-2147483648","-2147483649","2147483648","99999999999","null","true")) {
            var p=packages();String text=new String(p.getFirst().get("rules.json"),StandardCharsets.UTF_8);raw(p,0,"rules.json",text.replace("\"source_page\":59","\"source_page\":"+token));
            rejected(token.equals("null")?"S2_SHAPE":"S2_TYPE","AUTHOR",p);
        }
        for(int page:List.of(3,74)) {var p=packages();edit(p,0,"rules.json",a->row(a,0).addProperty("source_page",page));passes(p);}
        for(int page:List.of(0,2,75,Integer.MAX_VALUE)) {var p=packages();edit(p,0,"rules.json",a->row(a,0).addProperty("source_page",page));rejected("S2_TYPE","AUTHOR",p);}
        for(String token:List.of("\"false\"","0","1","null","[]","{}")) {
            var p=packages();edit(p,1,"changes.json",a->row(a,2).add("value",JsonParser.parseString(token)));rejected(token.equals("null")?"S2_SHAPE":"S2_TYPE","AUTHOR",p);
        }
        for(int minimum:List.of(0,129)) {var p=packages();edit(p,0,"rules.json",a->row(a,2).addProperty("minimum_enabled_members",minimum));rejected("S2_TYPE","AUTHOR",p);}
        for(int minimum:List.of(1,128)) {var p=packages();edit(p,0,"rules.json",a->row(a,2).addProperty("minimum_enabled_members",minimum));reads(p);}
    }

    @Test
    void scalarUnicodeNormalizesAuthorTextCountsCodePointsAndPreservesWhitespace() {
        for(String field:List.of("display_name","description","author_note")) {
            int limit=field.equals("display_name")?120:1000;
            var p=packages();edit(p,0,"rules.json",a->row(a,1).addProperty(field,"😀".repeat(limit)));passes(p);
            p=packages();edit(p,0,"rules.json",a->row(a,1).addProperty(field,"😀".repeat(limit+1)));rejected("S2_TEXT","AUTHOR",p);
            for(String value:List.of(""," ","\u00a0","\u2003","\u0000","\n","\t","\r","\u001f","\u007f","\u0080","\u0085","\u009f")) {
                p=packages();edit(p,0,"rules.json",a->row(a,1).addProperty(field,value));rejected("S2_TEXT","AUTHOR",p);
            }
        }
        var composed=packages();var decomposed=packages();edit(composed,0,"rules.json",a->row(a,1).addProperty("display_name","é"));edit(decomposed,0,"rules.json",a->row(a,1).addProperty("display_name","e\u0301"));
        var a=read(composed).getFirst();var b=read(decomposed).getFirst();assertEquals(a.logicalSha256(),b.logicalSha256());assertNotEquals(a.rawZipSha256(),b.rawZipSha256());
        assertArrayEquals(compose(composed).finalRulesJson(),compose(decomposed).finalRulesJson());
        var p=packages();edit(p,0,"rules.json",r->row(r,1).addProperty("display_name"," Dwarvish "));assertTrue(new String(compose(p).finalRulesJson(),StandardCharsets.UTF_8).contains(" Dwarvish "));
        for(String value:List.of("😀".repeat(121),"","\n","\u00a0")) {p=packages();edit(p,0,"author-package.json",h->h.getAsJsonObject().addProperty("package_display_name",value));rejected("S2_TEXT","AUTHOR",p);}
        p=packages();edit(p,0,"author-package.json",h->h.getAsJsonObject().addProperty("package_display_name","😀".repeat(120)));passes(p);
    }

    @Test
    void identitiesAndVersionsAreExactAsciiAndClosedTypedObjects() {
        for(String value:List.of("Validation_base","validation_base "," validation_base","ｖalidation_base","a".repeat(65),"a-b","")) {
            var p=packages();edit(p,0,"author-package.json",h->h.getAsJsonObject().addProperty("module_key",value));fails(p);
        }
        for(String version:List.of("","a".repeat(65)," 1","1 ","１","1/2")) {
            var p=packages();edit(p,0,"author-package.json",h->h.getAsJsonObject().addProperty("release_version",version));fails(p);
        }
        for(String version:List.of("01","1.0","vA","va","A_1-2.3")) {
            var p=packages();edit(p,0,"author-package.json",h->h.getAsJsonObject().addProperty("release_version",version));reads(p);
        }
        for(String field:List.of("rule_key","rule_type","module_key","kind")) {
            for(String variant:List.of("unknown","missing","null")) {
                var p=packages();edit(p,0,"rules.json",r->{var id=row(r,0).getAsJsonObject("identity");if(variant.equals("missing"))id.remove(field);else if(variant.equals("null"))id.add(field,JsonNull.INSTANCE);else id.addProperty(field,field.equals("rule_type")?"character.unknown":"BAD ");});
                fails(p);
            }
        }
        for(String category:List.of("standard","STANDARD ","UNKNOWN")){var p=packages();edit(p,0,"rules.json",a->row(a,0).addProperty("category",category));fails(p);}
        for(String key:List.of("Language.common","language-common","language.common ","a".repeat(129))){var p=packages();edit(p,0,"rules.json",a->row(a,0).getAsJsonObject("identity").addProperty("rule_key",key));fails(p);}
        for(String key:List.of("Common","common.dot","common ","a".repeat(65))){var p=packages();edit(p,0,"members.json",a->row(a,0).getAsJsonObject("identity").addProperty("entry_key",key));fails(p);}
        var p=packages();edit(p,0,"members.json",a->row(a,0).getAsJsonObject("identity").addProperty("collection_key","unknown"));rejected("S2_TYPE","AUTHOR",p);
    }

    @Test
    void closedDeclarationsRejectEnabledUnknownMissingNullAndMalformedOperations() {
        for(String path:List.of("rules.json","members.json"))for(String extra:List.of("unknown","enabled")) {
            var p=packages();edit(p,0,path,a->row(a,0).addProperty(extra,true));rejected("S2_SHAPE","AUTHOR",p);
        }
        for(String field:List.of("identity","display_name","description","category","source_page"))for(boolean nil:List.of(false,true)) {
            var p=packages();edit(p,0,"rules.json",a->{if(nil)row(a,0).add(field,JsonNull.INSTANCE);else row(a,0).remove(field);});rejected("S2_SHAPE","AUTHOR",p);
        }
        for(String field:List.of("identity","language"))for(boolean nil:List.of(false,true)) {
            var p=packages();edit(p,0,"members.json",a->{if(nil)row(a,0).add(field,JsonNull.INSTANCE);else row(a,0).remove(field);});rejected("S2_SHAPE","AUTHOR",p);
        }
        for(String op:List.of("delete","add","set_field ","SET_FIELD")){var p=packages();edit(p,1,"changes.json",a->row(a,0).addProperty("op",op));fails(p);}
        var p=packages();edit(p,1,"changes.json",a->row(a,0).addProperty("field","enabled"));rejected("S2_FIELD_UNKNOWN","AUTHOR",p);
        p=packages();edit(p,1,"changes.json",a->row(a,1).addProperty("value","not permitted"));rejected("S2_SHAPE","AUTHOR",p);
    }

    @Test
    void n28AllRawBudgetsArePreflightedAndExactByteLimitsAcceptValidPaddedFiles() {
        var valid=packages();pad(valid,0,"author-package.json",16384);pad(valid,0,"rules.json",65536);pad(valid,0,"members.json",65536);assertDoesNotThrow(()->compose(valid));
        for(String path:List.of("author-package.json","rules.json")) {
            var p=packages();pad(p,0,path,path.equals("author-package.json")?16385:65537);rejected("S2_BUDGET","PARSE",p);
        }
        var p=packages();while(p.size()<5)p.add(p.getFirst());rejected("S2_BUDGET","PARSE",p);
        p=packages();for(int i=0;i<17;i++)p.getFirst().put("extra"+i+".json",new byte[0]);rejected("S2_BUDGET","PARSE",p);
        p=packages();p.getFirst().clear();p.getFirst().put("author-package.json",new byte[16384]);for(int i=0;i<16;i++)p.getFirst().put("file"+i+".json",new byte[65536]);rejected("S2_BUDGET","PARSE",p);
        // Raw-length preflight precedes JSON parsing across every file, including invalid earlier JSON.
        p=packages();raw(p,0,"author-package.json","invalid");p.getFirst().put("rules.json",new byte[65537]);rejected("S2_BUDGET","PARSE",p);
    }

    @Test
    void sixteenAuthorFilesAndExactWholePackageByteBudgetRemainAccepted() {
        var p=packages();
        for(int i=0;i<14;i++)register(p,0,"empty"+i+".json","definitions",new JsonArray());
        int headerBytes=p.getFirst().get("author-package.json").length;int total=headerBytes;int index=0;
        for(String path:new ArrayList<>(p.getFirst().keySet()))if(!path.equals("author-package.json")){
            int size=index++==15?65536-headerBytes:65536;pad(p,0,path,size);total+=size;
        }
        assertEquals(1048576,total);passes(p);
    }

    @Test
    void n28ParserBudgetsCheckBeforeAppendingAndTokenLimitIsTestedIndependentlyOfRawByteLimit() {
        var source=new Schema2CompositionKernel.Location(null,null,null,"rules.json",null,null);
        assertDoesNotThrow(()->Schema2StrictJson.parse(("[".repeat(15)+"0"+"]".repeat(15)).getBytes(StandardCharsets.UTF_8),source));
        assertDoesNotThrow(()->Schema2StrictJson.parse(("\""+"a".repeat(4096)+"\"").getBytes(StandardCharsets.UTF_8),source));
        assertDoesNotThrow(()->Schema2StrictJson.parse(arrayOf(256,"0").getBytes(StandardCharsets.UTF_8),source));
        String object32="{"+String.join(",",java.util.stream.IntStream.range(0,32).mapToObj(i->"\"k"+i+"\":0").toList())+"}";
        assertDoesNotThrow(()->Schema2StrictJson.parse(object32.getBytes(StandardCharsets.UTF_8),source));
        String tokensAtLimit="["+String.join(",",Collections.nCopies(127,arrayOf(256,"0")))+","+arrayOf(127,"0")+"]";
        assertDoesNotThrow(()->Schema2StrictJson.parse(tokensAtLimit.getBytes(StandardCharsets.UTF_8),source));
        for(String over:List.of("[".repeat(16)+"0"+"]".repeat(16),"\""+"a".repeat(4097)+"\"",arrayOf(257,"0"),object32.substring(0,object32.length()-1)+",\"k32\":0}",tokensAtLimit.substring(0,tokensAtLimit.length()-2)+",0]]")) {
            var failure=assertThrows(Schema2CompositionKernel.Schema2Exception.class,()->Schema2StrictJson.parse(over.getBytes(StandardCharsets.UTF_8),source));assertEquals("S2_BUDGET",failure.diagnostic().code());assertEquals("PARSE",failure.diagnostic().stage().name());
        }
        assertTimeoutPreemptively(Duration.ofSeconds(2),()->{var p=packages();raw(p,0,"rules.json","[".repeat(10000));rejected("S2_BUDGET","PARSE",p);});
    }

    @Test
    void n28AggregateDefinitionEntryAndChangeBudgetsApplyAcrossFiles() {
        var p=packages();var original=row(json(p,0,"rules.json"),0);var rows=new JsonArray();
        for(int i=0;i<65;i++){var value=original.deepCopy();value.getAsJsonObject("identity").addProperty("rule_key","language.x"+i);rows.add(value);}put(p,0,"rules.json",rows);rejected("S2_BUDGET","PARSE",p);
        p=packages();original=row(json(p,0,"members.json"),0);rows=new JsonArray();
        for(int i=0;i<129;i++){var value=original.deepCopy();value.getAsJsonObject("identity").addProperty("entry_key","x"+i);rows.add(value);}put(p,0,"members.json",rows);rejected("S2_BUDGET","PARSE",p);
        p=packages();original=row(json(p,1,"changes.json"),0);rows=new JsonArray();var second=new JsonArray();
        for(int i=0;i<257;i++){var value=original.deepCopy();value.getAsJsonObject("target").addProperty("rule_key","language.x"+i);(i<128?rows:second).add(value);}put(p,1,"changes.json",rows);register(p,1,"second-changes.json","changes",second);rejected("S2_BUDGET","PARSE",p);
    }

    @Test
    void exactAggregateRowLimitsRemainReadable() {
        var p=packages();var definitions=json(p,0,"rules.json").getAsJsonArray();var template=definitions.get(0).getAsJsonObject();
        for(int i=0;i<61;i++){var value=template.deepCopy();value.getAsJsonObject("identity").addProperty("rule_key","language.extra"+i);definitions.add(value);}put(p,0,"rules.json",definitions);reads(p);
        p=packages();var entry=row(json(p,0,"members.json"),0);var entries=new JsonArray();
        for(int i=0;i<128;i++){var value=entry.deepCopy();value.getAsJsonObject("identity").addProperty("entry_key","x"+i);entries.add(value);}put(p,0,"members.json",entries);reads(p);
        p=packages();var change=row(json(p,1,"changes.json"),0);var first=new JsonArray();var second=new JsonArray();
        for(int i=0;i<256;i++){var value=change.deepCopy();value.getAsJsonObject("target").addProperty("rule_key","language.x"+i);(i<128?first:second).add(value);}put(p,1,"changes.json",first);register(p,1,"second-changes.json","changes",second);
        var author=KERNEL.read(p.get(1));assertEquals(256,author.changes().size());
    }

    private static void pad(List<Map<String,byte[]>> p,int index,String path,int size){byte[] raw=p.get(index).get(path),bytes=Arrays.copyOf(raw,size);Arrays.fill(bytes,raw.length,size,(byte)' ');p.get(index).put(path,bytes);}
    private static String arrayOf(int count,String value){return "["+String.join(",",Collections.nCopies(count,value))+"]";}
}
