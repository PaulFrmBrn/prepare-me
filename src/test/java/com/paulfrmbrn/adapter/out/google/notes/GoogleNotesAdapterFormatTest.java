package com.paulfrmbrn.adapter.out.google.notes;

import com.google.api.services.docs.v1.model.Bullet;
import com.google.api.services.docs.v1.model.Paragraph;
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

    private static Paragraph paragraph(Integer nestingLevel) {
        Paragraph p = new Paragraph();
        if (nestingLevel != null) {
            p.setBullet(new Bullet().setNestingLevel(nestingLevel));
        }
        return p;
    }
}
