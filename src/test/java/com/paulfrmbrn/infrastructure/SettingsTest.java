package com.paulfrmbrn.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsTest {

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
}
