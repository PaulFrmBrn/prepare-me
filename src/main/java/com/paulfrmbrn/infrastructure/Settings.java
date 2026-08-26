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

    /**
     * Content of {@code doc-mappings.yaml}: exact meeting titles and title-prefix rules.
     *
     * <pre>
     * titles:
     *   "Management Meeting": "_Notes/Teams/Senior Management team"
     * prefixes:
     *   "AIT:": "_Notes/Teams/AIT"
     * </pre>
     *
     * A prefix rule maps every meeting whose title starts with the prefix (e.g. every
     * {@code "AIT: ..."} team meeting) to one document; the longest matching prefix wins.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DocMappings {
        public Map<String, String> titles = new LinkedHashMap<>();
        public Map<String, String> prefixes = new LinkedHashMap<>();
    }

    public static Settings load(Path path) throws IOException {
        return new ObjectMapper(new YAMLFactory()).readValue(path.toFile(), Settings.class);
    }

    /** Loads a YAML list of excluded event names from the given file. Returns empty list if file does not exist. */
    public static List<String> loadExcludedEvents(Path path) throws IOException {
        if (!path.toFile().exists()) return new ArrayList<>();
        return new ObjectMapper(new YAMLFactory()).readValue(path.toFile(), new TypeReference<>() {});
    }

    /**
     * Loads doc mappings from the given YAML file. Returns empty mappings if the file does not exist.
     * A file without {@code titles:}/{@code prefixes:} sections is read as a flat legacy
     * {@code title: doc} map.
     */
    public static DocMappings loadDocMappings(Path path) throws IOException {
        var mappings = new DocMappings();
        if (!path.toFile().exists()) return mappings;
        Map<String, Object> raw = new ObjectMapper(new YAMLFactory()).readValue(path.toFile(), new TypeReference<>() {});
        if (raw == null || raw.isEmpty()) return mappings;
        if (!raw.containsKey("titles") && !raw.containsKey("prefixes")) {
            raw.forEach((title, doc) -> mappings.titles.put(title, String.valueOf(doc)));
            return mappings;
        }
        mappings.titles.putAll(asStringMap(raw.get("titles")));
        mappings.prefixes.putAll(asStringMap(raw.get("prefixes")));
        return mappings;
    }

    private static Map<String, String> asStringMap(Object section) {
        Map<String, String> result = new LinkedHashMap<>();
        if (section instanceof Map<?, ?> map) {
            map.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
        }
        return result;
    }

    /** Saves a list of excluded event names to the given YAML file, creating parent directories as needed. */
    public static void saveExcludedEvents(Path path, List<String> events) throws IOException {
        if (path.getParent() != null) java.nio.file.Files.createDirectories(path.getParent());
        new ObjectMapper(new YAMLFactory()).writeValue(path.toFile(), events);
    }

    /** Saves doc mappings to the given YAML file, creating parent directories as needed. */
    public static void saveDocMappings(Path path, DocMappings mappings) throws IOException {
        if (path.getParent() != null) java.nio.file.Files.createDirectories(path.getParent());
        new ObjectMapper(new YAMLFactory()).writeValue(path.toFile(), mappings);
    }

    /** Looks for settings.yaml in the current directory first, then ~/.prepare-me/. */
    public static Path defaultPath() {
        Path local = Path.of("settings.yaml");
        if (local.toFile().exists()) return local;
        return Path.of(System.getProperty("user.home"), ".prepare-me", "settings.yaml");
    }

    /** Looks for excluded-events.yaml in the current directory first, then falls back to the configured path. */
    public static Path resolveExcludedEventsPath(String configuredPath) {
        Path local = Path.of("excluded-events.yaml");
        if (local.toFile().exists()) return local;
        return Path.of(expand(configuredPath));
    }

    /** Looks for doc-mappings.yaml in the current directory first, then falls back to the configured path. */
    public static Path resolveDocMappingsPath(String configuredPath) {
        Path local = Path.of("doc-mappings.yaml");
        if (local.toFile().exists()) return local;
        return Path.of(expand(configuredPath));
    }

    /** Expands ~/... and leaves absolute and relative paths unchanged. */
    public static String expand(String path) {
        if (path != null && path.startsWith("~/")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }
}
