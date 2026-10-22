package com.dndtool.module.composition;

import com.google.gson.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.dndtool.module.composition.Schema2Fixtures.*;

class Schema2CompositionRejectionTest {
    @Test
    void n01n02n09n26RejectUnsupportedSchemaClosedManifestAndFileSet() {
        var p=packages();edit(p,0,"author-package.json",h->h.getAsJsonObject().addProperty("author_schema_version",1));
        pointer(rejected("S2_SCHEMA_UNSUPPORTED","AUTHOR",p),"author-package.json","/author_schema_version");
        for(String variant:List.of("extra","missing","null","COMPLETE","accepted")) {
            p=packages();edit(p,0,"author-package.json",h->{var o=h.getAsJsonObject();switch(variant){
                case "extra" -> o.addProperty("unknown",1);case "missing" -> o.remove("dependencies");case "null" -> o.add("dependencies",JsonNull.INSTANCE);
                case "COMPLETE" -> o.addProperty("verification_scope","COMPLETE");case "accepted" -> o.addProperty("accepted",true);
            }});
            String field=switch(variant){case "extra"->"unknown";case "COMPLETE"->"verification_scope";case "accepted"->"accepted";default->"dependencies";};
            pointer(rejected("S2_SHAPE","AUTHOR",p),"author-package.json","/"+field);
        }
        p=packages();raw(p,0,"rules.json","{}");at(rejected("S2_SHAPE","AUTHOR",p),0,"rules.json","");
        p=packages();p.getFirst().remove("members.json");pointer(rejected("S2_FILE_SET","AUTHOR",p),"author-package.json","/files");
        p=packages();raw(p,0,"unlisted.json","[]");rejected("S2_FILE_SET","AUTHOR",p);
    }

    @Test
    void n03ReportsBothDecodedDuplicateKeyOffsetsAndN04RejectsBomAndMalformedUtf8() {
        var p=packages();String header=new String(p.getFirst().get("author-package.json"),java.nio.charset.StandardCharsets.UTF_8);
        raw(p,0,"author-package.json",header.replace("\"module_key\":", "\"module_\\u006bey\":\"validation_base\",\"module_key\":"));
        var d=rejected("S2_DUPLICATE_KEY","PARSE",p);assertEquals(2,d.locations().size());
        for(var l:d.locations()){assertEquals("author-package.json",l.path());assertNotNull(l.byteOffset());assertNull(l.pointer());}
        assertNotEquals(d.locations().get(0).byteOffset(),d.locations().get(1).byteOffset());
        for(byte[] prefix:List.of(new byte[]{(byte)0xef,(byte)0xbb,(byte)0xbf},new byte[]{(byte)0xc0,(byte)0xaf})) {
            p=packages();byte[] raw=p.getFirst().get("author-package.json");byte[] bytes=Arrays.copyOf(prefix,prefix.length+raw.length);System.arraycopy(raw,0,bytes,prefix.length,raw.length);p.getFirst().put("author-package.json",bytes);
            d=rejected(prefix.length==3?"S2_JSON":"S2_UTF8","PARSE",p);assertEquals(0,d.locations().getFirst().byteOffset());
        }
    }

    @Test
    void n05n06n07n08n25RejectWrongTypesInvalidTextAndNonAtomicOrNonClearableFields() {
        var p=packages();edit(p,1,"changes.json",a->row(a,2).addProperty("value","false"));at(rejected("S2_TYPE","AUTHOR",p),1,"changes.json","/2/value");
        p=packages();String rules=new String(p.getFirst().get("rules.json"),java.nio.charset.StandardCharsets.UTF_8);raw(p,0,"rules.json",rules.replace("\"source_page\":59","\"source_page\":59.0"));at(rejected("S2_TYPE","AUTHOR",p),0,"rules.json","/0/source_page");
        p=packages();edit(p,0,"rules.json",a->row(a,0).addProperty("display_name",""));at(rejected("S2_TEXT","AUTHOR",p),0,"rules.json","/0/display_name");
        p=packages();raw(p,0,"rules.json",rules.replace("\"Common\"","\"\\ud800\""));rejected("S2_JSON","PARSE",p);
        p=packages();edit(p,0,"rules.json",a->row(a,0).addProperty("description",""));at(rejected("S2_TEXT","AUTHOR",p),0,"rules.json","/0/description");
        p=packages();edit(p,1,"changes.json",a->row(a,1).addProperty("field","description"));at(rejected("S2_FIELD_NOT_CLEARABLE","AUTHOR",p),1,"changes.json","/1/field");
        for(String field:List.of("identity","language.rule_key")) {
            p=packages();edit(p,1,"changes.json",a->row(a,0).addProperty("field",field));at(rejected("S2_FIELD_UNKNOWN","AUTHOR",p),1,"changes.json","/0/field");
        }
        p=packages();edit(p,1,"changes.json",a->row(a,0).addProperty("value",7));at(rejected("S2_TYPE","AUTHOR",p),1,"changes.json","/0/value");
    }

    @Test
    void n10n11n12RejectDuplicatesAndOriginalOwnerImpersonationWithBothSources() {
        var p=packages();edit(p,0,"rules.json",a->a.getAsJsonArray().add(a.getAsJsonArray().get(0).deepCopy()));
        var d=rejected("S2_DUPLICATE_IDENTITY","AUTHOR",p);at(d,0,"rules.json","/0");at(d,0,"rules.json","/3");
        p=packages();edit(p,1,"rules.json",a->row(a,0).getAsJsonObject("identity").addProperty("module_key",BASE));atOrBelow(rejected("S2_ORIGINAL_OWNER","AUTHOR",p),1,"rules.json","/0/identity");
        p=packages();var duplicate=new JsonArray();duplicate.add(row(json(p,1,"changes.json"),0).deepCopy());register(p,1,"second-changes.json","changes",duplicate);
        d=rejected("S2_DUPLICATE_FIELD","AUTHOR",p);at(d,1,"changes.json","/0");at(d,1,"second-changes.json","/0");
        p=packages();var duplicatedEntry=new JsonArray();duplicatedEntry.add(row(json(p,0,"members.json"),0).deepCopy());register(p,0,"second-members.json","entries",duplicatedEntry);
        d=rejected("S2_DUPLICATE_IDENTITY","AUTHOR",p);at(d,0,"members.json","/0");at(d,0,"second-members.json","/0");
    }

    @Test
    void n13n14RequirePreviousExistingTargetsAndDoNotLetLaterCorrectionsMaskFailure() {
        var p=packages();var self=identity(p,1,"rules.json",0);edit(p,1,"changes.json",a->row(a,0).add("target",self));
        atOrBelow(rejected("S2_TARGET_NOT_PREVIOUS","APPLY",p),1,"changes.json","/0");
        p=packages();edit(p,1,"changes.json",a->row(a,0).getAsJsonObject("target").addProperty("rule_key","language.missing"));
        atOrBelow(rejected("S2_TARGET_MISSING","APPLY",p),1,"changes.json","/0");
    }

    @Test
    void n15n16CheckEntryParentAndOriginalOwnerAsIndependentDirectDependencies() {
        for(int omitted:List.of(0,1)) {
            var p=packages();retainCompatClearOnly(p);
            edit(p,2,"author-package.json",h->{
                var o=h.getAsJsonObject();o.getAsJsonArray("dependencies").remove(omitted);var pair=o.getAsJsonArray("compatible_combinations").get(0).getAsJsonObject();pair.remove(omitted==0?BASE:EXT);
                if(omitted==1)o.add("load_order_constraints",new JsonArray());
            });
            atOrBelow(rejected("S2_DIRECT_DEPENDENCY","APPLY",p),2,"changes.json","/0");
        }
    }

    @Test
    void n17n18n19RequireCompleteJointPairingsAndExactVersionSets() {
        var p=packages();edit(p,2,"author-package.json",h->h.getAsJsonObject().getAsJsonArray("compatible_combinations").get(0).getAsJsonObject().remove(EXT));
        pointer(rejected("S2_COMBINATION_SHAPE","AUTHOR",p),"author-package.json","/compatible_combinations/0");
        p=packages();edit(p,2,"author-package.json",h->{var o=h.getAsJsonObject();for(var d:o.getAsJsonArray("dependencies"))d.getAsJsonObject().getAsJsonArray("supported_versions").add("2");
            var pair=new JsonObject();pair.addProperty(BASE,"2");pair.addProperty(EXT,"2");o.getAsJsonArray("compatible_combinations").add(pair);});
        edit(p,1,"author-package.json",h->h.getAsJsonObject().addProperty("release_version","2"));
        rejected("S2_COMBINATION_UNSUPPORTED","GRAPH",p);
        p=packages();edit(p,2,"author-package.json",h->h.getAsJsonObject().getAsJsonArray("dependencies").get(1).getAsJsonObject().getAsJsonArray("supported_versions").add("01"));
        pointer(rejected("S2_VERSION_SET_MISMATCH","AUTHOR",p),"author-package.json","/dependencies/1/supported_versions");
    }

    @Test
    void n20n21n22RejectDuplicateSelectionBadOrderCyclesDuplicateAndUnknownEdges() {
        var p=packages();p.add(p.get(1));var d=rejected("S2_DUPLICATE_MODULE","GRAPH",p);pointer(d,"$selection","/1");pointer(d,"$selection","/3");
        p=packages();Collections.swap(p,1,2);rejected("S2_LOAD_ORDER","GRAPH",p);
        p=packages();edit(p,2,"author-package.json",h->h.getAsJsonObject().getAsJsonArray("load_order_constraints").add(JsonParser.parseString("{\"before\":\"validation_compatibility\",\"after\":\"validation_base\"}")));
        rejected("S2_ORDER_CYCLE","GRAPH",p);
        p=packages();edit(p,1,"author-package.json",h->{var a=h.getAsJsonObject().getAsJsonArray("load_order_constraints");a.add(a.get(0).deepCopy());});
        d=rejected("S2_DUPLICATE_CONSTRAINT","AUTHOR",p);pointer(d,"author-package.json","/load_order_constraints/0");pointer(d,"author-package.json","/load_order_constraints/1");
        p=packages();edit(p,1,"author-package.json",h->h.getAsJsonObject().getAsJsonArray("load_order_constraints").get(0).getAsJsonObject().addProperty("after","unknown"));
        pointer(rejected("S2_ORDER_ENDPOINT","AUTHOR",p),"author-package.json","/load_order_constraints/0");
    }

    @Test
    void n23n29RejectInvalidReferencesWhenTheirPackageIsAppliedEvenIfLaterDisabled() {
        var p=packages();edit(p,0,"members.json",a->row(a,1).getAsJsonObject("language").addProperty("rule_key","language.missing"));
        var target=identity(p,0,"members.json",1);var op=new JsonObject();op.addProperty("op","set_enabled");op.addProperty("value",false);op.add("target",target);
        edit(p,1,"changes.json",a->a.getAsJsonArray().add(op));atOrBelow(rejected("S2_REFERENCE_MISSING","APPLY",p),0,"members.json","/1/language");
        p=packages();var future=identity(p,1,"rules.json",0);edit(p,0,"members.json",a->row(a,1).add("language",future));
        atOrBelow(rejected("S2_DIRECT_DEPENDENCY","APPLY",p),0,"members.json","/1/language");
    }

    @Test
    void n24n30CountEnabledDistinctTargetIdentitiesRatherThanRelationships() {
        var p=packages();edit(p,2,"changes.json",a->row(a,1).addProperty("value",false));rejected("S2_CANDIDATES_INSUFFICIENT","FINAL",p);
        p=packages();var dwarvish=identity(p,0,"rules.json",1);edit(p,2,"changes.json",a->row(a,5).add("value",dwarvish));rejected("S2_CANDIDATES_INSUFFICIENT","FINAL",p);
    }

    @Test
    void referencesCheckNewEntriesParentOwnerAndReferenceValueIndependently() {
        var p=packages();edit(p,1,"author-package.json",h->{var o=h.getAsJsonObject();o.add("dependencies",new JsonArray());var pair=new JsonArray();pair.add(new JsonObject());o.add("compatible_combinations",pair);o.add("load_order_constraints",new JsonArray());});
        // A dependency-free extension is rejected by author validation, before references.
        fails(p);
        p=packages();var selfLanguage=identity(p,1,"rules.json",0);edit(p,0,"members.json",a->{row(a,0).add("language",selfLanguage);});rejected("S2_DIRECT_DEPENDENCY","APPLY",p);
        p=packages();var set=identity(p,0,"rules.json",2);edit(p,0,"members.json",a->row(a,0).add("language",set));
        fails(p);
    }

    private static void retainCompatClearOnly(List<Map<String,byte[]>> p) {
        var original=json(p,2,"changes.json").getAsJsonArray();var retained=new JsonArray();retained.add(original.get(3).deepCopy());put(p,2,"changes.json",retained);
    }
    private static void pointer(Schema2CompositionKernel.Diagnostic d,String path,String pointer) {
        assertTrue(d.locations().stream().anyMatch(l->path.equals(l.path())&&l.pointer()!=null&&(pointer.equals(l.pointer())||l.pointer().startsWith(pointer+"/"))),
                ()->"Missing "+path+"#"+pointer+" in "+d.locations());
    }
}
