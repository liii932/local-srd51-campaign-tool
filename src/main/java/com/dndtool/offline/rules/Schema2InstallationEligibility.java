package com.dndtool.offline.rules;

import com.dndtool.module.composition.ProductionRuleArtifact;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Closed production gate. A finite projection is not a real runtime consumer. */
final class Schema2InstallationEligibility {
    private Schema2InstallationEligibility() { }
    static void requireEligible(ProductionRuleArtifact artifact) {
        String code = artifact.role() == Role.BASE ? "S2_BASE_INCOMPLETE" :
                !artifact.authorPackage().definitions().isEmpty() || !artifact.authorPackage().entries().isEmpty()
                        || !artifact.authorPackage().changes().isEmpty() ? "S2_MECHANISM_UNSUPPORTED" : null;
        if (code != null) throw new Schema2Exception(new Diagnostic(code, Stage.QUALIFICATION,
                code.equals("S2_BASE_INCOMPLETE") ? "No complete schema 2 base has integrated runtime consumers" :
                        "The declared schema 2 mechanisms have no integrated runtime consumers",
                java.util.List.of(new Location(artifact.moduleKey(), artifact.releaseVersion(),
                        artifact.rawZipSha256(), HEADER_PATH, "", null))));
    }
}
