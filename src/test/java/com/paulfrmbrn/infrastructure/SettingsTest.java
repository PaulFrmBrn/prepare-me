package com.paulfrmbrn.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class SettingsTest {

    @TempDir
    Path tempDir;

    @Test
    void expand_replacesTildeWithHomeDir() {
        String home = System.getProperty("user.home");
        assertThat(Settings.expand("~/.prepare-me/tokens")).isEqualTo(home + "/.prepare-me/tokens");
    }

    @Test
    void expand_leavesAbsolutePathUnchanged() {
        assertThat(Settings.expand("/absolute/path/file.json")).isEqualTo("/absolute/path/file.json");
    }

    @Test
    void expand_leavesRelativePathUnchanged() {
        assertThat(Settings.expand("google-credentials.json")).isEqualTo("google-credentials.json");
    }

    @Test
    void expand_returnsNullForNull() {
        assertThat(Settings.expand(null)).isNull();
    }

    @Test
    void expand_doesNotExpandTildeInMiddleOfPath() {
        assertThat(Settings.expand("/some/~/path")).isEqualTo("/some/~/path");
    }

    @Test
    void docMappings_roundTripKeepsBothSections() throws IOException {
        Path file = tempDir.resolve("doc-mappings.yaml");
        var mappings = new Settings.DocMappings();
        mappings.titles.put("Management Meeting", "_Notes/Teams/Senior Management team");
        mappings.prefixes.put("AIT:", "_Notes/Teams/AIT");
        Settings.saveDocMappings(file, mappings);

        var loaded = Settings.loadDocMappings(file);
        assertThat(loaded.titles).containsExactly(entry("Management Meeting", "_Notes/Teams/Senior Management team"));
        assertThat(loaded.prefixes).containsExactly(entry("AIT:", "_Notes/Teams/AIT"));
    }

    @Test
    void docMappings_readsLegacyFlatFileAsTitles() throws IOException {
        Path file = tempDir.resolve("doc-mappings.yaml");
        Files.writeString(file, "\"Weekly Sync\": Platform Team\n");

        var loaded = Settings.loadDocMappings(file);
        assertThat(loaded.titles).containsExactly(entry("Weekly Sync", "Platform Team"));
        assertThat(loaded.prefixes).isEmpty();
    }
}
