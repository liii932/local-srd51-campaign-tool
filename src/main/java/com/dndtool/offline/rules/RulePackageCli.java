package com.dndtool.offline.rules;

import com.dndtool.module.LanguagePartition;
import com.dndtool.module.ToolPartition;
import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.module.composition.Schema2CompositionKernel.Schema2Exception;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/** Standalone author artifact inspection and assembly. */
public final class RulePackageCli {
    private RulePackageCli() { }

    public static void main(String[] args) throws IOException {
        int code = run(args);
        if (code != 0) System.exit(code);
    }

    static int run(String[] args) throws IOException {
        if (args.length == 3 && args[0].equals("build")) {
            System.out.println("installation_manifest_sha256=" + RuleArtifact.build(Path.of(args[1]), Path.of(args[2])));
            return 0;
        }
        if (args.length == 3 && args[0].equals("verify")) {
            RuleArtifact artifact = RuleArtifact.read(Path.of(args[1]), args[2]);
            var author = artifact.author();
            System.out.printf(Locale.ROOT, "%s %s=%d %s=%d manifest=%s%n", author.verificationScope(),
                    LanguagePartition.KEY, author.languages().languages().size(),
                    ToolPartition.KEY, author.tools().tools().size(), artifact.manifestSha256());
            return 0;
        }
        if (args.length == 2 && args[0].equals("schema2-verify")) return verifySchema2(args[1]);

        System.err.println("Usage: build SOURCE NEW_OUTPUT | verify ARTIFACT MANIFEST_SHA256 | schema2-verify ZIP");
        return 2;
    }

    private static int verifySchema2(String path) {
        try {
            var artifact = ProductionRuleArtifact.read(SecureFiles.read(Path.of(path), ProductionRuleArtifact.MAX_ZIP_BYTES));
            System.out.println("Verified transport only: " + artifact.moduleKey() + "@" + artifact.releaseVersion()
                    + "; role=" + artifact.role() + "; profile=" + artifact.canonicalProfile()
                    + "; raw_zip_sha256=" + artifact.rawZipSha256() + "; logical_sha256=" + artifact.logicalSha256());
            return 0;
        } catch (Schema2Exception invalid) {
            System.err.println("ERROR code=" + invalid.diagnostic().code() + " stage=" + invalid.diagnostic().stage());
        } catch (IOException failure) {
            System.err.println("ERROR code=S2_LOCAL_STATE");
        } catch (IllegalArgumentException invalid) {
            System.err.println("ERROR code=S2_INPUT_OR_AUTHORIZATION");
        }
        return 2;
    }
}
