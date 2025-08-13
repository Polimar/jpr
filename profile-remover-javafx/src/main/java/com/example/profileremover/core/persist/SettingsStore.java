package com.example.profileremover.core.persist;

import com.example.profileremover.core.model.AppSettings;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

public class SettingsStore {
    private final Path path = Path.of(System.getenv("LOCALAPPDATA"), "ProfileRemover", "settings.json");
    private final ObjectMapper om = new ObjectMapper();

    public AppSettings load() {
        try {
            if (Files.exists(path)) return om.readValue(Files.readString(path), AppSettings.class);
        } catch (Exception ignored) {}
        return new AppSettings();
    }

    public void save(AppSettings s) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, om.writerWithDefaultPrettyPrinter().writeValueAsString(s));
    }
}


