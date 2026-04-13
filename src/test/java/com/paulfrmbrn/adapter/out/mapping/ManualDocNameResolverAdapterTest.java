package com.paulfrmbrn.adapter.out.mapping;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManualDocNameResolverAdapterTest {

    @Test
    void returnsDocNameFromMapping() {
        var adapter = new ManualDocNameResolverAdapter(Map.of("Weekly Sync", "Platform Team"));

        assertThat(adapter.resolveDocName("Weekly Sync")).isEqualTo("Platform Team");
    }

    @Test
    void throwsMissingDocMappingExceptionWhenNotFound() {
        var adapter = new ManualDocNameResolverAdapter(Map.of());

        assertThatThrownBy(() -> adapter.resolveDocName("Monthly Review"))
                .isInstanceOf(MissingDocMappingException.class)
                .hasMessageContaining("Monthly Review")
                .hasMessageContaining("settings.yaml");
    }
}
