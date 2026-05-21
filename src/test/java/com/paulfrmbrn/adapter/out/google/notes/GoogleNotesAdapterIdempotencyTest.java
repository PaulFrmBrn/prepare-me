package com.paulfrmbrn.adapter.out.google.notes;

import com.google.api.services.docs.v1.model.Body;
import com.google.api.services.docs.v1.model.Document;
import com.google.api.services.docs.v1.model.Paragraph;
import com.google.api.services.docs.v1.model.ParagraphElement;
import com.google.api.services.docs.v1.model.ParagraphStyle;
import com.google.api.services.docs.v1.model.RichLink;
import com.google.api.services.docs.v1.model.RichLinkProperties;
import com.google.api.services.docs.v1.model.StructuralElement;
import com.google.api.services.docs.v1.model.TextRun;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleNotesAdapterIdempotencyTest {

    static final LocalDate DATE = LocalDate.of(2026, 5, 21);
    static final String MEETING = "Ivan / Dima";

    @Test
    void noContent_returnsFalse() {
        var doc = new Document().setBody(new Body().setContent(null));
        assertThat(GoogleNotesAdapter.agendaEntryExists(doc, MEETING, DATE)).isFalse();
    }

    @Test
    void heading1WithMeetingNameButNoRichLink_returnsFalse() {
        var doc = docWithHeading("HEADING_1", MEETING, null, null);
        assertThat(GoogleNotesAdapter.agendaEntryExists(doc, MEETING, DATE)).isFalse();
    }

    @Test
    void heading1WithMeetingNameAndMatchingDateInTitle_returnsTrue() {
        var doc = docWithHeading("HEADING_1", MEETING, DATE.toString(), null);
        assertThat(GoogleNotesAdapter.agendaEntryExists(doc, MEETING, DATE)).isTrue();
    }

    @Test
    void heading1WithMeetingNameAndMatchingDateInUri_returnsTrue() {
        var doc = docWithHeading("HEADING_1", MEETING, "May 21, 2026",
                "https://calendar.google.com/calendar/r/day/2026/5/21");
        assertThat(GoogleNotesAdapter.agendaEntryExists(doc, MEETING, DATE)).isFalse(); // title doesn't match
        // Now with ISO date in URI
        var doc2 = docWithHeading("HEADING_1", MEETING, "irrelevant",
                "https://calendar.google.com/r/day?date=2026-05-21");
        assertThat(GoogleNotesAdapter.agendaEntryExists(doc2, MEETING, DATE)).isTrue();
    }

    @Test
    void heading1WithMeetingNameAndDifferentDate_returnsFalse() {
        var doc = docWithHeading("HEADING_1", MEETING, "2026-05-20", null);
        assertThat(GoogleNotesAdapter.agendaEntryExists(doc, MEETING, DATE)).isFalse();
    }

    @Test
    void heading1WithDifferentMeetingNameAndMatchingDate_returnsFalse() {
        var doc = docWithHeading("HEADING_1", "Other Meeting", DATE.toString(), null);
        assertThat(GoogleNotesAdapter.agendaEntryExists(doc, MEETING, DATE)).isFalse();
    }

    @Test
    void normalParagraphWithMeetingNameAndMatchingDate_returnsFalse() {
        var doc = docWithHeading("NORMAL_TEXT", MEETING, DATE.toString(), null);
        assertThat(GoogleNotesAdapter.agendaEntryExists(doc, MEETING, DATE)).isFalse();
    }

    @Test
    void heading2WithMeetingNameAndMatchingDate_returnsFalse() {
        var doc = docWithHeading("HEADING_2", MEETING, DATE.toString(), null);
        assertThat(GoogleNotesAdapter.agendaEntryExists(doc, MEETING, DATE)).isFalse();
    }

    // Builds a Document with a single paragraph of the given heading style, containing both
    // a text run with the meeting name and (optionally) a date chip rich link.
    private static Document docWithHeading(String headingStyle, String meetingName,
                                           String chipTitle, String chipUri) {
        var textElement = new ParagraphElement()
                .setTextRun(new TextRun().setContent(" " + meetingName + "\n"));

        List<ParagraphElement> elements;
        if (chipTitle != null || chipUri != null) {
            var props = new RichLinkProperties();
            if (chipTitle != null) props.setTitle(chipTitle);
            if (chipUri != null) props.setUri(chipUri);
            var chip = new ParagraphElement().setRichLink(new RichLink().setRichLinkProperties(props));
            elements = List.of(chip, textElement);
        } else {
            elements = List.of(textElement);
        }

        var paragraph = new Paragraph()
                .setElements(elements)
                .setParagraphStyle(new ParagraphStyle().setNamedStyleType(headingStyle));
        var structural = new StructuralElement().setParagraph(paragraph);
        return new Document().setBody(new Body().setContent(List.of(structural)));
    }
}
