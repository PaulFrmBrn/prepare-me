package com.paulfrmbrn.domain.usecase;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Merged from the duplicated {@code extractOtherPersonName} cases that
 * {@code PrepareMeetingNotesTest} and {@code SaveMeetingNotesTest} each used to carry
 * against their own copy of the pattern.
 */
class OneOnOneTitleTest {

    @Nested
    class OtherPersonName {

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @CsvSource({
                "Ivan / Dima,                    Ivan",
                "Dmitry / Maria,                 Maria",
                "Dmitrii / Olga,                 Olga",
                "Darias/Dmitry,                  Darias",
        })
        void plainTitle(String title, String expected) {
            assertThat(OneOnOneTitle.otherPersonName(title)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @CsvSource({
                "Oleg K / Dmitry,                Oleg K",
                "Egor A / Dmitry: Random chat 2, Egor A",
                "Dmitry / Egor A: Random chat,   Egor A",
                "Oleg K. / Dmitry,               Oleg K.",
                "ODM-12259. Oleg K / Dmitry,     Oleg K",
        })
        void multiWordName(String title, String expected) {
            assertThat(OneOnOneTitle.otherPersonName(title)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @CsvSource({
                "ODM-12259. Mikhail / Dmitry,    Mikhail",
                "Random chat 2 - Gleb / Dmitry,  Gleb",
                "1-1 Ivan / Dmitry,              Ivan",
        })
        void delimitedPrefixIsStripped(String title, String expected) {
            assertThat(OneOnOneTitle.otherPersonName(title)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @CsvSource({
                "Ivan / Dmitry - milestone 3,            Ivan",
                "Ivan / Dmitry: Databricks prep call,    Ivan",
        })
        void trailingSubjectIsIgnored(String title, String expected) {
            assertThat(OneOnOneTitle.otherPersonName(title)).isEqualTo(expected);
        }

        @Test
        void throwsOnTitleThatIsNotOneOnOne() {
            assertThatThrownBy(() -> OneOnOneTitle.otherPersonName("Standup"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Standup");
        }
    }

    @Nested
    class Matches {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "Ivan / Dima",
                "Dmitry / Maria",
                "Darias/Dmitry",
                "Oleg K / Dmitry",
                "ODM-12259. Mikhail / Dmitry",
                "Ivan / Dmitry - milestone 3",
        })
        void oneOnOneTitles(String title) {
            assertThat(OneOnOneTitle.matches(title)).isTrue();
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "Standup",
                "Weekly Sync",
                "A / B / Dmitry",   // third party before the user
                "Dmitry / A / B",   // third party after the user
        })
        void groupTitles(String title) {
            assertThat(OneOnOneTitle.matches(title)).isFalse();
        }
    }
}
