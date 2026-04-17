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
    void topicWithNoChecklists_writesTopicLine() {
        var result = AgendaFormatter.format(MEETING, List.of(new Topic("Deploy", List.of())));
        assertThat(result).isEqualTo("""

                 Team Sync
                > Deploy
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
                """);
    }

    @Test
    void singleChecklistNamedChecklist_allItemsChecked_omitsChecklist() {
        var checklist = new Checklist("Checklist", Optional.empty());
        var result = AgendaFormatter.format(MEETING, List.of(new Topic("Bug fix", List.of(checklist))));
        assertThat(result).isEqualTo("""

                 Team Sync
                > Bug fix
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
                """);
    }

    @Test
    void singleNamedChecklist_noUncheckedItems_omitsChecklist() {
        var checklist = new Checklist("Prep", Optional.empty());
        var result = AgendaFormatter.format(MEETING, List.of(new Topic("Sprint planning", List.of(checklist))));
        assertThat(result).isEqualTo("""

                 Team Sync
                > Sprint planning
                """);
    }

    @Test
    void multipleChecklists_skipsChecklistsWithNoUncheckedItems() {
        var cl1 = new Checklist("Prep", Optional.of("Book room"));
        var cl2 = new Checklist("Goals", Optional.empty());
        var result = AgendaFormatter.format(MEETING, List.of(new Topic("Sprint planning", List.of(cl1, cl2))));
        assertThat(result).isEqualTo("""

                 Team Sync
                > Sprint planning
                >> Prep
                Book room
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
                > Review
                Read PR
                """);
    }

    @Test
    void emptyTopicList_writesNoTopics() {
        var result = AgendaFormatter.format(MEETING, List.of());
        assertThat(result).isEqualTo("""

                 Team Sync
                > No topics
                """);
    }
}
