package com.dndtool.module;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

/** The supported language/tool subset; never a complete or executable rule release. */
public record CharacterCatalogPartition(LanguagePartition languages, ToolPartition tools) {
    public CharacterCatalogPartition {
        Objects.requireNonNull(languages);
        Objects.requireNonNull(tools);
    }

    /** Uses the single shared logical model; this projection carries only PARTITION coverage. */
    public ModuleCatalog projection() {
        var definitions=new ArrayList<ModuleCatalog.CatalogDefinition>();
        var attributes=new ArrayList<ModuleCatalog.CatalogAttribute>();
        for(var row:languages.languages()) {
            definitions.add(new ModuleCatalog.CatalogDefinition(LanguagePartition.KEY,row.languageKey(),row.displayName(),row.description(),row.sortOrder()));
            attributes(attributes,LanguagePartition.KEY,row.languageKey(),row.category().name(),row.sourcePage());
        }
        for(var row:tools.tools()) {
            definitions.add(new ModuleCatalog.CatalogDefinition(ToolPartition.KEY,row.toolKey(),row.displayName(),row.description(),row.sortOrder()));
            attributes(attributes,ToolPartition.KEY,row.toolKey(),row.category().name(),row.sourcePage());
        }
        return new ModuleCatalog(new ModuleCatalog.Release("dnd5e2014_srd51_se","1",2,"SHA-256",null,"DRAFT"),
                List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),
                List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),
                List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),definitions,attributes,List.of());
    }
    private static void attributes(List<ModuleCatalog.CatalogAttribute> rows,String type,String key,String category,int page) {
        rows.add(new ModuleCatalog.CatalogAttribute(type,key,"catalog.category",1,"IDENTIFIER",new ModuleCatalog.IdentifierValue(category)));
        rows.add(new ModuleCatalog.CatalogAttribute(type,key,"source.page",1,"INTEGER",new ModuleCatalog.IntegerValue(page)));
    }
}
