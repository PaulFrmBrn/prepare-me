package com.paulfrmbrn.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Path;

public class Settings {

    public Google google = new Google();
    public Trello trello = new Trello();

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
