package com.dndtool.module.composition;

import java.io.IOException;
import java.util.*;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Fixed independent equipment and direct health source vectors. */
public final class Schema2EquipmentHealthFixtures {
    private Schema2EquipmentHealthFixtures() { }
    public static List<Map<String,byte[]>> packages() {
        List<Map<String,byte[]>> result=new ArrayList<>();
        for(String module:List.of("vitals_base","vitals_extension")) {
            Map<String,byte[]> files=new LinkedHashMap<>();files.put(HEADER_PATH,resource(module+"/"+HEADER_PATH));
            String path=module.equals("vitals_base")?"rules.json":"changes.json";files.put(path,resource(module+"/"+path));result.add(files);
        }
        return result;
    }
    public static List<ProductionRuleArtifact> artifacts(){return Schema2GrantFixtures.artifacts(packages());}
    public static ProductionSchema2Projection projection(){return ProductionSchema2Projection.compose(artifacts());}
    private static byte[] resource(String path){try(var input=Schema2EquipmentHealthFixtures.class.getResourceAsStream("/schema2-equipment-health/"+path)){if(input==null)throw new AssertionError(path);return input.readAllBytes();}catch(IOException failure){throw new AssertionError(failure);}}
}
