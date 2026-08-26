package com.paulfrmbrn.adapter.out.mapping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManualDocNameResolverAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsDocNameFromLegacyFlatMapping() throws IOException {
        Path file = tempDir.resolve("doc-mappings.yaml");
        Files.writeString(file, "\"Weekly Sync\": Platform Team\n");
        var adapter = new ManualDocNameResolverAdapter(file);

        assertThat(adapter.resolveDocName("Weekly Sync")).isEqualTo("Platform Team");
    }

    @Test
    void throwsMissingDocMappingExceptionWhenNotFound() throws IOException {
        Path file = tempDir.resolve("doc-mappings.yaml");
        Files.writeString(file, "{}\n");
        var adapter = new ManualDocNameResolverAdapter(file);

        assertThatThrownBy(() -> adapter.resolveDocName("Monthly Review"))
                .isInstanceOf(MissingDocMappingException.class)
                .hasMessageContaining("Monthly Review")
                .hasMessageContaining("doc-mappings.yaml");
    }

    @Test
    void returnsDocNameFromTitlesSection() throws IOException {
        Path file = tempDir.resolve("doc-mappings.yaml");
        Files.writeString(file, "titles:\n  \"Weekly Sync\": Platform Team\n");
        var adapter = new ManualDocNameResolverAdapter(file);

        assertThat(adapter.resolveDocName("Weekly Sync")).isEqualTo("Platform Team");
    }

    @Test
    void returnsDocNameByTitlePrefix() throws IOException {
        Path file = tempDir.resolve("doc-mappings.yaml");
        Files.writeString(file, "prefixes:\n  \"AIT:\": \"_Notes/Teams/AIT\"\n");
        var adapter = new ManualDocNameResolverAdapter(file);

        assertThat(adapter.resolveDocName("AIT: Sync on AIT-8")).isEqualTo("_Notes/Teams/AIT");
    }

    @Test
    void prefersExactTitleOverPrefix() throws IOException {
        Path file = tempDir.resolve("doc-mappings.yaml");
        Files.writeString(file, """
                titles:
                  "AIT: Retro": "_Notes/Teams/AIT retro"
                prefixes:
                  "AIT:": "_Notes/Teams/AIT"
                """);
        var adapter = new ManualDocNameResolverAdapter(file);

        assertThat(adapter.resolveDocName("AIT: Retro")).isEqualTo("_Notes/Teams/AIT retro");
    }

    @Test
    void prefersLongestMatchingPrefix() throws IOException {
        Path file = tempDir.resolve("doc-mappings.yaml");
        Files.writeString(file, """
                prefixes:
                  "AIT:": "_Notes/Teams/AIT"
                  "AIT: Copilot": "_Notes/Teams/AIT Copilot"
                """);
        var adapter = new ManualDocNameResolverAdapter(file);

        assertThat(adapter.resolveDocName("AIT: Copilot weekly")).isEqualTo("_Notes/Teams/AIT Copilot");
        assertThat(adapter.resolveDocName("AIT: Sprint review")).isEqualTo("_Notes/Teams/AIT");
    }

    @Test
    void matchesTicketIdPrefixLiterally() throws IOException {
        Path file = tempDir.resolve("doc-mappings.yaml");
        Files.writeString(file, """
                prefixes:
                  "ODM-12259.": "_Notes/Teams/ODM"
                """);
        var adapter = new ManualDocNameResolverAdapter(file);

        assertThat(adapter.resolveDocName("ODM-12259. Sprint Review")).isEqualTo("_Notes/Teams/ODM");
        // "." is a literal character here, not a regex wildcard
        assertThatThrownBy(() -> adapter.resolveDocName("ODM-12259X Sprint Review"))
                .isInstanceOf(MissingDocMappingException.class);
    }

    @Test
    void throwsWhenNoPrefixMatches() throws IOException {
        Path file = tempDir.resolve("doc-mappings.yaml");
        Files.writeString(file, "prefixes:\n  \"AIT:\": \"_Notes/Teams/AIT\"\n");
        var adapter = new ManualDocNameResolverAdapter(file);

        assertThatThrownBy(() -> adapter.resolveDocName("ODM: Sprint review"))
                .isInstanceOf(MissingDocMappingException.class);
    }

    @Test
    void returnsEmptyMappingWhenFileDoesNotExist() {
        Path file = tempDir.resolve("nonexistent.yaml");
        var adapter = new ManualDocNameResolverAdapter(file);

        assertThatThrownBy(() -> adapter.resolveDocName("Some Meeting"))
                .isInstanceOf(MissingDocMappingException.class);
    }
}
