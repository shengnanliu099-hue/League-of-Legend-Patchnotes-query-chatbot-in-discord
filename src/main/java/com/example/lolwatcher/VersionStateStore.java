package com.example.lolwatcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class VersionStateStore {
    private final Path stateFile;

    public VersionStateStore(Path stateFile) {
        this.stateFile = stateFile;
    }

    public Optional<String> readLastVersion() throws IOException {
        if (!Files.exists(stateFile)) {
            return Optional.empty();
        }
        String value = Files.readString(stateFile, StandardCharsets.UTF_8).trim();
        if (value.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    public void saveLastVersion(String version) throws IOException {
        Path parent = stateFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(stateFile, version + System.lineSeparator(), StandardCharsets.UTF_8);
    }
}
