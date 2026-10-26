package com.dndtool.module.composition;

import java.io.IOException;
import java.util.*;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Fixed independent combat/melee/walking-speed source vectors. */
public final class Schema2CombatFixtures {
    private Schema2CombatFixtures() { }
    public static List<Map<String,byte[]>> packages() {
        List<Map<String,byte[]>> result=new ArrayList<>();
        for(String module:List.of("combat_base","combat_extension")) {
            Map<String,byte[]> files=new LinkedHashMap<>();files.put(HEADER_PATH,resource(module+"/"+HEADER_PATH));
            String path=module.equals("combat_base")?"rules.json":"changes.json";files.put(path,resource(module+"/"+path));result.add(files);
        }
        return result;
    }
    public static List<ProductionRuleArtifact> artifacts(){return Schema2GrantFixtures.artifacts(packages());}
    public static ProductionSchema2Projection projection(){return ProductionSchema2Projection.compose(artifacts());}
    private static byte[] resource(String path){try(var input=Schema2CombatFixtures.class.getResourceAsStream("/schema2-combat/"+path)){if(input==null)throw new AssertionError(path);return input.readAllBytes();}catch(IOException failure){throw new AssertionError(failure);}}
}
