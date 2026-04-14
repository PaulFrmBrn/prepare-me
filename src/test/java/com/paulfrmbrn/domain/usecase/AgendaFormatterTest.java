package com.paulfrmbrn.domain.usecase;

import com.paulfrmbrn.domain.model.Checklist;
import com.paulfrmbrn.domain.model.Topic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AgendaFormatterTest {

    static final String MEETING = "Team Sync";

    @Test
    void topicWithNoChecklists_writesTopicLineAndNotesBlock() {
        var result = AgendaFormatter.format(MEETING, List.of(new Topic("Deploy", List.of())));
        assertThat(result).isEqualTo("""

                 Team Sync
                > Deploy
                n>
                <n
                """);
    }

    @Test
    void singleChecklistNamedChecklist_withUncheckedItem_omitsChecklistHeader() {
        var checklist = new Checklist("Checklist", Optional.of("Fix tests"));
        var result = AgendaFormatter.format(MEETING, List.of(new Topic("Bug fix", List.of(checklist))));
        assertThat(result).isEqualTo("""

                 Team Sync
                > Bug fix
                Fix tests
                n>
                <n
                """);
    }

    @Test
    void singleChecklistNamedChecklist_allItemsChecked_writesNoItems() {
        var checklist = new Checklist("Checklist", Optional.empty());
        var result = AgendaFormatter.format(MEETING, List.of(new Topic("Bug fix", List.of(checklist))));
        assertThat(result).isEqualTo("""

                 Team Sync
                > Bug fix
                no items
                n>
                <n
                """);
    }

    @Test
    void singleNamedChecklist_writesChecklistHeader() {
        var checklist = new Checklist("Prep", Optional.of("Slide deck"));
        var result = AgendaFormatter.format(MEETING, List.of(new Topic("Sprint planning", List.of(checklist))));
        assertThat(result).isEqualTo("""

                 Team Sync
                > Sprint planning
                >> Prep
                Slide deck
                n>
                <n
                """);
    }

    @Test
    void singleNamedChecklist_noUncheckedItems_writesNoItems() {
        var checklist = new Checklist("Prep", Optional.empty());
        var result = AgendaFormatter.format(MEETING, List.of(new Topic("Sprint planning", List.of(checklist))));
        assertThat(result).isEqualTo("""

                 Team Sync
                > Sprint planning
                >> Prep
                no items
                n>
                <n
                """);
    }

    @Test
    void multipleChecklists_writesAllHeaders() {
        var cl1 = new Checklist("Prep", Optional.of("Book room"));
        var cl2 = new Checklist("Goals", Optional.empty());
        var result = AgendaFormatter.format(MEETING, List.of(new Topic("Sprint planning", List.of(cl1, cl2))));
        assertThat(result).isEqualTo("""

                 Team Sync
                > Sprint planning
                >> Prep
                Book room
                n>
                <n
                >> Goals
                no items
                n>
                <n
                """);
    }

    @Test
    void multipleTopics_writesAllTopics() {
        var result = AgendaFormatter.format(MEETING, List.of(
                new Topic("Deploy", List.of()),
                new Topic("Review", List.of(new Checklist("Checklist", Optional.of("Read PR"))))
        ));
        assertThat(result).isEqualTo("""

                 Team Sync
                > Deploy
                n>
                <n
                > Review
                Read PR
                n>
                <n
                """);
    }

    @Test
    void emptyTopicList_writesNoTopicsAndNotesBlock() {
        var result = AgendaFormatter.format(MEETING, List.of());
        assertThat(result).isEqualTo("""

                 Team Sync
                > No topics
                n>
                <n
                """);
    }
}
