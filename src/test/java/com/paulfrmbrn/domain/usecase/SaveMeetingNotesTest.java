package com.paulfrmbrn.domain.usecase;

import com.paulfrmbrn.adapter.out.mapping.MissingDocMappingException;
import com.paulfrmbrn.domain.model.DocRef;
import com.paulfrmbrn.domain.model.Meeting;
import com.paulfrmbrn.domain.model.MeetingWithTopics;
import com.paulfrmbrn.domain.model.Topic;
import com.paulfrmbrn.domain.model.TopicContent;
import com.paulfrmbrn.domain.port.out.CalendarPort;
import com.paulfrmbrn.domain.port.out.ManualLinkResolverPort;
import com.paulfrmbrn.domain.port.out.MeetingBoardPort;
import com.paulfrmbrn.domain.port.out.MeetingNotesPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaveMeetingNotesTest {

    static final LocalDate DATE = LocalDate.of(2026, 4, 14);
    static final String NOTES_DIR = "_Notes";

    @Mock MeetingBoardPort board;
    @Mock CalendarPort calendar;
    @Mock MeetingNotesPort notes;
    @Mock ManualLinkResolverPort resolver;

    SaveMeetingNotes useCase;

    @BeforeEach
    void setUp() {
        useCase = new SaveMeetingNotes(board, calendar, notes, resolver, NOTES_DIR);
    }

    @Test
    void oneOnOneMeeting_readsNotesAndPostsComments() {
        var topics = List.of(new Topic("t1", "Deploy", List.of()));
        when(board.getMeetingsWithTopics()).thenReturn(List.of(
                new MeetingWithTopics("Meeting: Ivan / Dima", topics)
        ));
        when(calendar.getMeetings(DATE)).thenReturn(List.of(
                new Meeting("Ivan / Dima", List.of("ivan@x.com", "dima@x.com"))
        ));
        when(resolver.resolveDocName("Ivan / Dima")).thenThrow(new MissingDocMappingException("Ivan / Dima"));
        var docRef = new DocRef("doc1", "https://docs.google.com/doc1");
        when(notes.findDoc("_Notes/People/Ivan")).thenReturn(Optional.of(docRef));
        when(notes.readTopicNotes(docRef, DATE, "Ivan / Dima")).thenReturn(
                List.of(new TopicContent("Deploy", "Notify team\nDone"))
        );

        var result = useCase.execute(DATE);

        assertThat(result).containsExactly(Map.entry("Ivan / Dima", 1));
        verify(notes).readTopicNotes(docRef, DATE, "Ivan / Dima");
        verify(board).addTopicComment("t1", "Notify team\nDone");
    }

    @Test
    void skipsTopicWhenNoMatchingCardFound() {
        var topics = List.of(new Topic("t1", "Deploy", List.of()));
        when(board.getMeetingsWithTopics()).thenReturn(List.of(
                new MeetingWithTopics("Meeting: Ivan / Dima", topics)
        ));
        when(calendar.getMeetings(DATE)).thenReturn(List.of(
                new Meeting("Ivan / Dima", List.of("ivan@x.com", "dima@x.com"))
        ));
        when(resolver.resolveDocName("Ivan / Dima")).thenThrow(new MissingDocMappingException("Ivan / Dima"));
        var docRef = new DocRef("doc1", "https://docs.google.com/doc1");
        when(notes.findDoc("_Notes/People/Ivan")).thenReturn(Optional.of(docRef));
        when(notes.readTopicNotes(docRef, DATE, "Ivan / Dima")).thenReturn(
                List.of(new TopicContent("UnknownTopic", "some notes"))
        );

        var result = useCase.execute(DATE);

        assertThat(result).containsExactly(Map.entry("Ivan / Dima", 0));
        verify(board, never()).addTopicComment(any(), any());
    }

    @Test
    void groupMeeting_skippedWhenMappingMissing() {
        when(board.getMeetingsWithTopics()).thenReturn(List.of(
                new MeetingWithTopics("Meeting: Weekly Sync", List.of())
        ));
        when(calendar.getMeetings(DATE)).thenReturn(List.of(
                new Meeting("Weekly Sync", List.of("a@x.com", "b@x.com", "c@x.com"))
        ));
        when(resolver.resolveDocName("Weekly Sync")).thenThrow(new MissingDocMappingException("Weekly Sync"));

        var result = useCase.execute(DATE);

        assertThat(result).isEmpty();
        verifyNoInteractions(notes);
    }

    @Test
    void skipsWhenDocNotFound() {
        when(board.getMeetingsWithTopics()).thenReturn(List.of(
                new MeetingWithTopics("Meeting: Ivan / Dima", List.of())
        ));
        when(calendar.getMeetings(DATE)).thenReturn(List.of(
                new Meeting("Ivan / Dima", List.of("ivan@x.com", "dima@x.com"))
        ));
        when(resolver.resolveDocName("Ivan / Dima")).thenThrow(new MissingDocMappingException("Ivan / Dima"));
        when(notes.findDoc("_Notes/People/Ivan")).thenReturn(Optional.empty());

        var result = useCase.execute(DATE);

        assertThat(result).isEmpty();
        verify(notes, never()).readTopicNotes(any(), any(), any());
    }

    @Test
    void returnsEmptyWhenNoMeetings() {
        when(board.getMeetingsWithTopics()).thenReturn(List.of());
        when(calendar.getMeetings(DATE)).thenReturn(List.of());

        var result = useCase.execute(DATE);

        assertThat(result).isEmpty();
        verifyNoInteractions(notes);
    }

    @Test
    void extractOtherPersonName_nameBeforeDima() {
        assertThat(useCase.extractOtherPersonName("Ivan / Dima")).isEqualTo("Ivan");
    }
}
