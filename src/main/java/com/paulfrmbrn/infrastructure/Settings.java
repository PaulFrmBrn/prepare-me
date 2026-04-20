package com.paulfrmbrn.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Settings {

    public Google google = new Google();
    public Trello trello = new Trello();
    public String notesDir = "_Notes";
    public String excludedEventsFile = "~/.prepare-me/excluded-events.yaml";
    public String docMappingsFile = "~/.prepare-me/doc-mappings.yaml";

    public static class Google {
        public String credentialsFile = "~/.prepare-me/google-credentials.json";
        public String tokensDir = "~/.prepare-me/tokens";
    }

    public static class Trello {
        public String apiKey;
        public String apiToken;
        public String boardName = "work";
        public String meetingsListName = "Meetings";
    }

    public static Settings load(Path path) throws IOException {
        return new ObjectMapper(new YAMLFactory()).readValue(path.toFile(), Settings.class);
    }

    /** Loads a YAML list of excluded event names from the given file. Returns empty list if file does not exist. */
    public static List<String> loadExcludedEvents(Path path) throws IOException {
        if (!path.toFile().exists()) return new ArrayList<>();
        return new ObjectMapper(new YAMLFactory()).readValue(path.toFile(), new TypeReference<>() {});
    }

    /** Loads a YAML map of doc mappings from the given file. Returns empty map if file does not exist. */
    public static Map<String, String> loadDocMappings(Path path) throws IOException {
        if (!path.toFile().exists()) return new LinkedHashMap<>();
        return new ObjectMapper(new YAMLFactory()).readValue(path.toFile(), new TypeReference<>() {});
    }

    /** Looks for settings.yaml in the current directory first, then ~/.prepare-me/. */
    public static Path defaultPath() {
        Path local = Path.of("settings.yaml");
        if (local.toFile().exists()) return local;
        return Path.of(System.getProperty("user.home"), ".prepare-me", "settings.yaml");
    }

    /** Expands ~/... and leaves absolute and relative paths unchanged. */
    public static String expand(String path) {
        if (path != null && path.startsWith("~/")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }
}
