package com.paulfrmbrn.adapter.out.google.notes;

import com.google.api.services.docs.v1.model.Bullet;
import com.google.api.services.docs.v1.model.Paragraph;
import com.google.api.services.docs.v1.model.ParagraphStyle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleNotesAdapterFormatTest {

    @Test
    void plainParagraph_returnedAsIs() {
        assertThat(GoogleNotesAdapter.formatBodyLine(paragraph(null), "some text"))
                .isEqualTo("some text");
    }

    @Test
    void bulletLevel0_prefixedWithDash() {
        assertThat(GoogleNotesAdapter.formatBodyLine(paragraph(0), "item"))
                .isEqualTo("- item");
    }

    @Test
    void bulletLevel1_indentedOnce() {
        assertThat(GoogleNotesAdapter.formatBodyLine(paragraph(1), "nested"))
                .isEqualTo("  - nested");
    }

    @Test
    void bulletLevel2_indentedTwice() {
        assertThat(GoogleNotesAdapter.formatBodyLine(paragraph(2), "deep"))
                .isEqualTo("    - deep");
    }

    @Test
    void bulletNullNestingLevel_treatedAsLevel0() {
        Paragraph p = new Paragraph().setBullet(new Bullet());  // nestingLevel not set
        assertThat(GoogleNotesAdapter.formatBodyLine(p, "item"))
                .isEqualTo("- item");
    }

    @Test
    void heading_renderedAsBoldBecauseTrelloHasNoHeadings() {
        assertThat(GoogleNotesAdapter.formatBodyLine(heading("HEADING_3", null), "\u0421\u043b\u0435\u0434\u0443\u044e\u0449\u0438\u0435 \u0448\u0430\u0433\u0438"))
                .isEqualTo("**\u0421\u043b\u0435\u0434\u0443\u044e\u0449\u0438\u0435 \u0448\u0430\u0433\u0438**");
        assertThat(GoogleNotesAdapter.formatBodyLine(heading("TITLE", null), "Title"))
                .isEqualTo("**Title**");
    }

    @Test
    void heading_trailingSpaceKeptOutsideBoldMarkers() {
        assertThat(GoogleNotesAdapter.formatBodyLine(heading("HEADING_3", null), "Next steps  "))
                .isEqualTo("**Next steps**");
    }

    @Test
    void bulletedHeading_keepsBulletAndAddsBold() {
        assertThat(GoogleNotesAdapter.formatBodyLine(heading("HEADING_3", 1), "nested heading"))
                .isEqualTo("  - **nested heading**");
    }

    @Test
    void standaloneHeading_detectedOnlyWithoutBullet() {
        assertThat(GoogleNotesAdapter.isStandaloneHeading(heading("HEADING_3", null))).isTrue();
        assertThat(GoogleNotesAdapter.isStandaloneHeading(heading("HEADING_3", 0))).isFalse();
        assertThat(GoogleNotesAdapter.isStandaloneHeading(heading("NORMAL_TEXT", null))).isFalse();
        assertThat(GoogleNotesAdapter.isStandaloneHeading(paragraph(null))).isFalse();
    }

    @Test
    void topicBody_separatesHeadingFromSurroundingBulletsWithBlankLines() {
        var body = new GoogleNotesAdapter.TopicBody();
        body.append(paragraph(0), "first item");
        body.append(paragraph(1), "nested item");
        body.append(heading("HEADING_3", null), "Next steps");
        body.append(paragraph(0), "follow-up item");

        // Blank lines around the heading stop Trello folding it into "nested item"
        assertThat(body.toString()).isEqualTo("""
                - first item
                  - nested item

                **Next steps**

                - follow-up item""");
    }

    @Test
    void topicBody_keepsConsecutiveBulletsOnAdjacentLines() {
        var body = new GoogleNotesAdapter.TopicBody();
        body.append(paragraph(0), "one");
        body.append(paragraph(0), "two");

        assertThat(body.toString()).isEqualTo("- one\n- two");
    }

    @Test
    void topicBody_headingFirstHasNoLeadingBlankLine() {
        var body = new GoogleNotesAdapter.TopicBody();
        body.append(heading("HEADING_3", null), "Next steps");
        body.append(paragraph(0), "item");

        assertThat(body.toString()).isEqualTo("**Next steps**\n\n- item");
    }

    private static Paragraph heading(String namedStyleType, Integer nestingLevel) {
        Paragraph p = paragraph(nestingLevel);
        return p.setParagraphStyle(new ParagraphStyle().setNamedStyleType(namedStyleType));
    }

    private static Paragraph paragraph(Integer nestingLevel) {
        Paragraph p = new Paragraph();
        if (nestingLevel != null) {
            p.setBullet(new Bullet().setNestingLevel(nestingLevel));
        }
        return p;
    }
}
