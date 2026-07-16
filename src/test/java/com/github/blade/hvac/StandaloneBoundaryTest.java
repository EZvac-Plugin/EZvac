package com.github.blade.hvac;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StandaloneBoundaryTest {
    @Test
    void productionAndPublicFilesContainOnlyEzvacIdentity() throws IOException {
        Path project = Path.of("").toAbsolutePath();
        List<Path> publicRoots = List.of(
                project.resolve("src/main"),
                project.resolve("docs"),
                project.resolve(".github"),
                project.resolve("README.md"),
                project.resolve("commands.txt"),
                project.resolve("features.txt"),
                project.resolve("pom.xml")
        );
        List<String> forbidden = List.of(
                String.join("", "wi", "fi", "ez"),
                String.join("", "wi", "fi"),
                String.join("-", "wi", "fi"),
                String.join("", "ss", "id"),
                String.join("-", "player", "network"),
                String.join("-", "smart", "switch")
        );
        List<Path> violations = new ArrayList<>();

        for (Path root : publicRoots) {
            if (Files.isRegularFile(root)) {
                inspect(root, forbidden, violations);
            } else if (Files.isDirectory(root)) {
                try (Stream<Path> files = Files.walk(root)) {
                    for (Path file : files.filter(Files::isRegularFile).toList())
                        inspect(file, forbidden, violations);
                }
            }
        }

        assertTrue(violations.isEmpty(),
                () -> "Foreign product markers found in: " + violations);
    }

    private static void inspect(Path file, List<String> forbidden, List<Path> violations)
            throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        if (forbidden.stream().anyMatch(content::contains)) violations.add(file);
    }
}
