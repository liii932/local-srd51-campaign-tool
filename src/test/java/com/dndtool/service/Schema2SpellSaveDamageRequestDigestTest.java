package com.dndtool.service;

import com.dndtool.module.composition.*;
import com.dndtool.module.composition.Schema2CompositionKernel.*;
import com.dndtool.persistence.JdbcSchema2CombatRuntimeRepository.Member;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import com.dndtool.service.Schema2CombatRuntimeService.Request;
import com.dndtool.service.Schema2CombatState.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Schema2SpellSaveDamageRequestDigestTest {
    private static UUID id(int n){return UUID.fromString(String.format(Locale.ROOT,"00000000-0000-4000-8000-%012d",n));}
    private static RuleIdentity rule(String type,String key){return new RuleIdentity("base",type,key);}
    private static Request request(CastInput cast) {
        var binding=new Schema2RulesBinding(ProductionSchema2Projection.CANONICAL_PROFILE,ProductionSchema2Projection.COMPOSITION_ALGORITHM,
                List.of(new ProductionSchema2Projection.StoredMember("base","1",Role.BASE,"1".repeat(64),"2".repeat(64))),"3".repeat(64),"4".repeat(64),"5".repeat(64));
        return new Request(id(1),id(2),id(3),binding,List.of(new Member(id(4),new Identity(id(6),id(7)),id(8)),new Member(id(5),new Identity(id(9),id(10)),id(11))),7,Map.of(id(4),11L,id(5),13L),Command.cast(id(4),cast));
    }
    private static String digest(Request request)throws Exception {
        var method=Schema2CombatRuntimeService.class.getDeclaredMethod("fingerprint",Request.class);method.setAccessible(true);return (String)method.invoke(null,request);
    }
    @Test void fixedLegacyNoAreaVectorRetainsItsOldBytesAndDirectionsHaveDistinctIntents()throws Exception {
        // Independent pre-area v3 layout: 890 bytes, UTF-8 text with int length, UUID presence
        // followed by 16 bytes, ordered members 4/5 and no area field at all.
        var old=new CastInput(rule("character.spellcasting","spellcasting.life_domain"),rule("adventure.spell","spell.bless"),List.of(id(5)),id(12));
        assertEquals("0dd3b1a284874b20f2b822de01d7271f3dcc5ba5fd52adf329597ec6d736ff72",digest(request(old)));
        var east=new CastInput(rule("character.wizard_spellcasting","spellcasting.wizard"),rule("adventure.spell","spell.burning_hands"),List.of(),null,1,null,0);
        var northeast=new CastInput(east.castingRule(),east.spell(),List.of(),null,1,null,1);
        assertNotEquals(digest(request(old)),digest(request(east)));assertNotEquals(digest(request(east)),digest(request(northeast)));
    }
}
