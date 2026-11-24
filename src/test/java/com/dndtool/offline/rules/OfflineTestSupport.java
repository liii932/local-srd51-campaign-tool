package com.dndtool.offline.rules;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.util.List;

final class OfflineTestSupport {
    static boolean supportsSecureDirectoryHandles(Path directory) throws IOException {
        try (var stream = Files.newDirectoryStream(directory)) {
            return stream instanceof SecureDirectoryStream<?>;
        }
    }

    private static Path authorDirectory(Path parent) throws IOException {
        Path fixture = Path.of("src/test/resources/character-catalog-author");
        Path source = Files.createDirectory(parent.resolve("author"));
        Files.createDirectory(source.resolve("character"));
        for (String file : List.of("author-package.json", "character/languages.json", "character/tools.json")) {
            Files.copy(fixture.resolve(file), source.resolve(file));
        }
        Files.writeString(source.resolve("package-guide.md"), "Documentation\n");
        Files.writeString(source.resolve("notice.md"), "License\n");
        return source;
    }
    static RuleArtifact artifact(Path parent) throws IOException {
        assumeTrue(supportsSecureDirectoryHandles(parent), "Author artifact files need secure directory handles");
        Path source = authorDirectory(parent);
        Path output = parent.resolve("artifact");
        String hash = RuleArtifact.build(source, output);
        return RuleArtifact.read(output, hash);
    }
}
