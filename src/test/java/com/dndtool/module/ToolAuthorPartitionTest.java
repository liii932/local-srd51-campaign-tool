package com.dndtool.module;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolAuthorPartitionTest {
    private static final Path SOURCE=Path.of("rule-packages/srd51-complete");
    private CharacterCatalogAuthorPackageReader.Result read(byte[] tools) throws Exception {
        return new CharacterCatalogAuthorPackageReader().read(Files.readAllBytes(SOURCE.resolve("author-package.json")),
                Files.readAllBytes(SOURCE.resolve("character/languages.json")),tools);
    }
    private byte[] bytes() throws Exception { return Files.readAllBytes(SOURCE.resolve("character/tools.json")); }
    private JsonArray json() throws Exception {return JsonParser.parseString(new String(bytes(),StandardCharsets.UTF_8)).getAsJsonArray();}
    private byte[] bytes(JsonArray json) {return json.toString().getBytes(StandardCharsets.UTF_8);}

    @Test void formalToolsMatchAllIndependentFieldsAndCombinedCanonicalBytes() throws Exception {
        var actual=read(bytes());var expected=ToolCatalogOracle.rows();
        assertEquals(37,actual.tools().tools().size());
        for(int i=0;i<37;i++) {
            var a=actual.tools().tools().get(i);var e=expected.get(i);
            assertEquals(e.key(),a.toolKey());assertEquals(e.name(),a.displayName());assertEquals(e.description(),a.description());
            assertEquals(e.category(),a.category().name());assertEquals(e.page(),a.sourcePage());assertEquals(e.order(),a.sortOrder());
        }
        var partition=new CharacterCatalogPartition(actual.languages(),actual.tools());
        assertEquals(55,partition.projection().catalogDefinitions().size());
        assertEquals(110,partition.projection().catalogAttributes().size());
        assertArrayEquals(HexFormat.of().parseHex(Files.readString(Path.of("src/test/resources/module-canonical-v2-language-tools.hex")).strip()),
                new ModuleCanonicalEncoderV2().encode(partition.projection()));
        assertEquals("PARTITION",actual.verificationScope());
        assertEquals(BuiltinModuleReleaseRegistry.ResolutionStatus.UNPUBLISHED_RELEASE,
                new BuiltinModuleReleaseRegistry().resolveReleased("dnd5e2014_srd51_se","1").status());
    }

    @Test void closedFieldsKeysCategoriesAndFullCardinalityAreRequired() throws Exception {
        for(String key:List.of("tool_key","display_name","description","category","source_page","sort_order")) {
            var input=json();input.get(0).getAsJsonObject().remove(key);assertThrows(IllegalArgumentException.class,()->read(bytes(input)));
            var nul=json();nul.get(0).getAsJsonObject().add(key,null);assertThrows(IllegalArgumentException.class,()->read(bytes(nul)));
        }
        for(String category:List.of("UNKNOWN","ARTISAN ","KIT","artisan")) {
            var input=json();input.get(0).getAsJsonObject().addProperty("category",category);
            assertThrows(IllegalArgumentException.class,()->read(bytes(input)));
        }
        var missing=json();missing.remove(0);assertThrows(IllegalArgumentException.class,()->read(bytes(missing)));
        var extra=json();extra.add(extra.get(0));assertThrows(IllegalArgumentException.class,()->read(bytes(extra)));
        var duplicate=json();duplicate.set(1,duplicate.get(0));assertThrows(IllegalArgumentException.class,()->read(bytes(duplicate)));
        var unknown=json();unknown.get(0).getAsJsonObject().addProperty("extra",1);assertThrows(IllegalArgumentException.class,()->read(bytes(unknown)));
        String raw=new String(bytes(),StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,()->read(raw.replaceFirst("\"tool_key\"", "\"tool_key\":\"tool.fake\",\"tool_key\"").getBytes(StandardCharsets.UTF_8)));
    }

    @Test void unicodeIntegerLexemesAndBudgetsFailClosed() throws Exception {
        for(String field:List.of("display_name","description")) {
            int limit=field.equals("display_name")?120:1000;
            var legal=json();legal.get(0).getAsJsonObject().addProperty(field,"😀".repeat(limit));assertDoesNotThrow(()->read(bytes(legal)));
            for(String bad:List.of("😀".repeat(limit+1),"","\u00a0","\u0000","\u007f","\u0085","\n")) {
                var input=json();input.get(0).getAsJsonObject().addProperty(field,bad);assertThrows(IllegalArgumentException.class,()->read(bytes(input)));
            }
            var surrogate=json();surrogate.get(0).getAsJsonObject().addProperty(field,"SURROGATE");
            String malformed=surrogate.toString().replace("SURROGATE","\\ud800");
            assertThrows(IllegalArgumentException.class,()->read(malformed.getBytes(StandardCharsets.UTF_8)));
        }
        for(String token:List.of("0","-1","70.0","7e1","\"70\"","true","null","2147483648")) {
            String bad=new String(bytes(),StandardCharsets.UTF_8).replaceFirst("\"source_page\": 70","\"source_page\": "+token);
            assertThrows(IllegalArgumentException.class,()->read(bad.getBytes(StandardCharsets.UTF_8)));
        }
        assertThrows(IllegalArgumentException.class,()->read(null));
        assertThrows(IllegalArgumentException.class,()->read(new byte[CharacterCatalogAuthorPackageReader.MAX_TOOL_BYTES+1]));
        assertThrows(IllegalArgumentException.class,()->read(new byte[]{(byte)0xc0,(byte)0xaf}));
        byte[] padded=Arrays.copyOf(bytes(),CharacterCatalogAuthorPackageReader.MAX_TOOL_BYTES);
        Arrays.fill(padded,bytes().length,padded.length,(byte)' ');assertEquals(read(bytes()).tools(),read(padded).tools());
    }

    @Test void explicitOrderAndImmutabilityAreIndependentOfInputArrayOrder() throws Exception {
        var actual=read(bytes());var rows=new ArrayList<>(actual.tools().tools());Collections.reverse(rows);
        assertEquals(actual.tools(),new ToolPartition(rows));rows.clear();assertEquals(37,actual.tools().tools().size());
        assertThrows(UnsupportedOperationException.class,()->actual.tools().tools().clear());
        for(String field:List.of("display_name","description","source_page","sort_order")) {
            var input=json();var first=input.get(0).getAsJsonObject();
            if(field.equals("source_page"))first.addProperty(field,71);
            else if(field.equals("sort_order")){first.addProperty(field,2);input.get(1).getAsJsonObject().addProperty(field,1);}
            else first.addProperty(field,"Changed");
            var changed=read(bytes(input));
            assertFalse(Arrays.equals(new ModuleCanonicalEncoderV2().encode(new CharacterCatalogPartition(actual.languages(),actual.tools()).projection()),
                    new ModuleCanonicalEncoderV2().encode(new CharacterCatalogPartition(changed.languages(),changed.tools()).projection())));
        }
    }
}
