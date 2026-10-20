package com.paulfrmbrn.adapter.out.google.notes;

import com.google.api.services.docs.v1.model.Body;
import com.google.api.services.docs.v1.model.Document;
import com.google.api.services.docs.v1.model.Paragraph;
import com.google.api.services.docs.v1.model.ParagraphElement;
import com.google.api.services.docs.v1.model.ParagraphStyle;
import com.google.api.services.docs.v1.model.RichLink;
import com.google.api.services.docs.v1.model.RichLinkProperties;
import com.google.api.services.docs.v1.model.NamedRange;
import com.google.api.services.docs.v1.model.NamedRanges;
import com.google.api.services.docs.v1.model.Range;
import com.google.api.services.docs.v1.model.StructuralElement;
import com.google.api.services.docs.v1.model.TextRun;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleNotesAdapterIdempotencyTest {

    static final LocalDate DATE = LocalDate.of(2026, 5, 21);
    static final String MEETING = "Ivan / Dima";

    @Test
    void agendaRangeName_isUniquePerMeetingAndDate() {
        assertThat(GoogleNotesAdapter.agendaRangeName(MEETING, DATE))
                .isEqualTo("prepare-me:agenda:2026-05-21:Ivan / Dima");
        assertThat(GoogleNotesAdapter.agendaRangeName(MEETING, DATE.plusDays(1)))
                .isNotEqualTo(GoogleNotesAdapter.agendaRangeName(MEETING, DATE));
        assertThat(GoogleNotesAdapter.agendaRangeName("Other / Dima", DATE))
                .isNotEqualTo(GoogleNotesAdapter.agendaRangeName(MEETING, DATE));
    }

    @Test
    void agendaRangeName_truncatesToDocsNameLimit() {
        String name = GoogleNotesAdapter.agendaRangeName("x".repeat(400), DATE);
        assertThat(name).hasSize(256).startsWith("prepare-me:agenda:2026-05-21:");
    }

    private static NamedRanges markerCovering(int start, int end) {
        return new NamedRanges().setNamedRanges(List.of(new NamedRange()
                .setRanges(List.of(new Range().setStartIndex(start).setEndIndex(end)))));
    }

    @Test
    void agendaMarked_trueForMarkerCoveringLiveContent() {
        String name = GoogleNotesAdapter.agendaRangeName(MEETING, DATE);
        assertThat(GoogleNotesAdapter.agendaMarked(Map.of(name, markerCovering(10, 24)), name)).isTrue();
    }

    @Test
    void agendaMarked_falseWhenMarkerAbsent() {
        String name = GoogleNotesAdapter.agendaRangeName(MEETING, DATE);
        assertThat(GoogleNotesAdapter.agendaMarked(null, name)).isFalse();
        assertThat(GoogleNotesAdapter.agendaMarked(Map.of(), name)).isFalse();
        assertThat(GoogleNotesAdapter.agendaMarked(
                Map.of("prepare-me:agenda:2026-05-20:Ivan / Dima", markerCovering(10, 24)), name)).isFalse();
    }

    @Test
    void agendaMarked_falseWhenMarkerEmptied() {
        // Entry deleted by hand: an emptied range must not suppress the re-append.
        String name = GoogleNotesAdapter.agendaRangeName(MEETING, DATE);
        assertThat(GoogleNotesAdapter.agendaMarked(Map.of(name, markerCovering(10, 10)), name)).isFalse();
        assertThat(GoogleNotesAdapter.agendaMarked(Map.of(name, new NamedRanges()), name)).isFalse();
        assertThat(GoogleNotesAdapter.agendaMarked(
                Map.of(name, new NamedRanges().setNamedRanges(List.of(new NamedRange()))), name)).isFalse();
    }

    @Test
    void hasAgendaMarker_falseWhenAbsentOrUnmarked() {
        assertThat(GoogleNotesAdapter.hasAgendaMarker(null)).isFalse();
        assertThat(GoogleNotesAdapter.hasAgendaMarker(Map.of())).isFalse();
        assertThat(GoogleNotesAdapter.hasAgendaMarker(Map.of("some-other-range", new NamedRanges()))).isFalse();
    }

    @Test
    void hasAgendaMarker_trueForAnyAgendaRange() {
        assertThat(GoogleNotesAdapter.hasAgendaMarker(
                Map.of(GoogleNotesAdapter.agendaRangeName(MEETING, DATE), new NamedRanges()))).isTrue();
    }

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
