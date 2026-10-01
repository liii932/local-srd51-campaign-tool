package com.dndtool.module;

import java.util.ArrayList;
import java.util.List;

/** Handwritten field oracle, independent of author JSON, migrations and production mappings. */
public final class ToolCatalogOracle {
    private ToolCatalogOracle() {}
    private static final String MATRIX="""
            alchemist_supplies|Alchemist Supplies|ARTISAN
            bagpipes|Bagpipes|MUSICAL_INSTRUMENT
            brewer_supplies|Brewer Supplies|ARTISAN
            calligrapher_supplies|Calligrapher Supplies|ARTISAN
            carpenter_tools|Carpenter Tools|ARTISAN
            cartographer_tools|Cartographer Tools|ARTISAN
            cobbler_tools|Cobbler Tools|ARTISAN
            cook_utensils|Cook Utensils|ARTISAN
            dice_set|Dice Set|GAMING_SET
            disguise_kit|Disguise Kit|KIT
            drum|Drum|MUSICAL_INSTRUMENT
            dulcimer|Dulcimer|MUSICAL_INSTRUMENT
            flute|Flute|MUSICAL_INSTRUMENT
            forgery_kit|Forgery Kit|KIT
            glassblower_tools|Glassblower Tools|ARTISAN
            herbalism_kit|Herbalism Kit|KIT
            horn|Horn|MUSICAL_INSTRUMENT
            jeweler_tools|Jeweler Tools|ARTISAN
            leatherworker_tools|Leatherworker Tools|ARTISAN
            lute|Lute|MUSICAL_INSTRUMENT
            lyre|Lyre|MUSICAL_INSTRUMENT
            mason_tools|Mason Tools|ARTISAN
            navigator_tools|Navigator Tools|NAVIGATION
            painter_supplies|Painter Supplies|ARTISAN
            pan_flute|Pan Flute|MUSICAL_INSTRUMENT
            playing_card_set|Playing Card Set|GAMING_SET
            poisoner_kit|Poisoner Kit|KIT
            potter_tools|Potter Tools|ARTISAN
            shawm|Shawm|MUSICAL_INSTRUMENT
            smith_tools|Smith Tools|ARTISAN
            thieves_tools|Thieves Tools|KIT
            tinker_tools|Tinker Tools|ARTISAN
            vehicles_land|Vehicles (Land)|VEHICLE
            vehicles_water|Vehicles (Water)|VEHICLE
            viol|Viol|MUSICAL_INSTRUMENT
            weaver_tools|Weaver Tools|ARTISAN
            woodcarver_tools|Woodcarver Tools|ARTISAN
            """;
    public record Row(String key,String name,String description,String category,int page,int order) {}
    public static List<Row> rows() {
        var rows=new ArrayList<Row>();
        for(String line:MATRIX.strip().split("\n")) {
            String[] cells=line.split("\\|");
            rows.add(new Row("tool."+cells[0],cells[1],cells[1]+" is an SRD 5.1 tool catalog entry.",cells[2],70,rows.size()+1));
        }
        return List.copyOf(rows);
    }
}
