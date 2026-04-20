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
    void returnsDocNameFromMapping() throws IOException {
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
    void returnsEmptyMappingWhenFileDoesNotExist() {
        Path file = tempDir.resolve("nonexistent.yaml");
        var adapter = new ManualDocNameResolverAdapter(file);

        assertThatThrownBy(() -> adapter.resolveDocName("Some Meeting"))
                .isInstanceOf(MissingDocMappingException.class);
    }
}
