package com.paulfrmbrn.domain.usecase;

import com.paulfrmbrn.adapter.out.mapping.MissingDocMappingException;
import com.paulfrmbrn.domain.model.DocRef;
import com.paulfrmbrn.domain.model.Meeting;
import com.paulfrmbrn.domain.model.MeetingType;
import com.paulfrmbrn.domain.model.MeetingWithTopics;
import com.paulfrmbrn.domain.model.Topic;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrepareMeetingNotesTest {

    static final LocalDate DATE = LocalDate.of(2026, 4, 14);
    static final String NOTES_DIR = "_Notes";

    @Mock MeetingBoardPort board;
    @Mock CalendarPort calendar;
    @Mock MeetingNotesPort notes;
    @Mock ManualLinkResolverPort resolver;

    PrepareMeetingNotes useCase;

    @BeforeEach
    void setUp() {
        useCase = new PrepareMeetingNotes(board, calendar, notes, resolver, NOTES_DIR);
    }

    @Test
    void oneOnOneMeeting_buildsPathFromNotesDirAndAppendsAgenda() {
        var topics = List.of(new Topic("topic1", List.of()), new Topic("topic2", List.of()));
        when(board.getMeetingsWithTopics()).thenReturn(List.of(
                new MeetingWithTopics("Meeting: Ivan / Dima", topics)
        ));
        when(calendar.getMeetings(DATE)).thenReturn(List.of(
                new Meeting("Ivan / Dima", List.of("ivan@example.com", "dima@example.com"))
        ));
        when(resolver.resolveDocName("Ivan / Dima")).thenThrow(new MissingDocMappingException("Ivan / Dima"));
        var docRef = new DocRef("doc1", "https://docs.google.com/doc1");
        when(notes.findDoc("_Notes/People/Ivan")).thenReturn(Optional.of(docRef));

        var result = useCase.execute(DATE);

        assertThat(result).containsExactly(Map.entry("Ivan / Dima", "https://docs.google.com/doc1"));
        verify(notes).findDoc("_Notes/People/Ivan");
        verify(notes).appendAgenda(docRef, DATE, "Ivan / Dima", topics);
    }

    @Test
    void oneOnOneMeeting_withDocMapping_usesDocMappingPath() {
        when(board.getMeetingsWithTopics()).thenReturn(List.of(
                new MeetingWithTopics("Meeting: Ivan / Dima", List.of())
        ));
        when(calendar.getMeetings(DATE)).thenReturn(List.of(
                new Meeting("Ivan / Dima", List.of("ivan@example.com", "dima@example.com"))
        ));
        when(resolver.resolveDocName("Ivan / Dima")).thenReturn("_Notes/Custom/Ivan override");
        var docRef = new DocRef("doc1", "https://docs.google.com/doc1");
        when(notes.findDoc("_Notes/Custom/Ivan override")).thenReturn(Optional.of(docRef));

        var result = useCase.execute(DATE);

        assertThat(result).containsExactly(Map.entry("Ivan / Dima", "https://docs.google.com/doc1"));
        verify(notes).findDoc("_Notes/Custom/Ivan override");
    }

    @Test
    void oneOnOneMeeting_userNameFirst_buildsCorrectPath() {
        when(board.getMeetingsWithTopics()).thenReturn(List.of(
                new MeetingWithTopics("Meeting: Dmitry / Maria", List.of())
        ));
        when(calendar.getMeetings(DATE)).thenReturn(List.of(
                new Meeting("Dmitry / Maria", List.of("dmitry@example.com", "maria@example.com"))
        ));
        when(resolver.resolveDocName("Dmitry / Maria")).thenThrow(new MissingDocMappingException("Dmitry / Maria"));
        var docRef = new DocRef("doc2", "https://docs.google.com/doc2");
        when(notes.findDoc("_Notes/People/Maria")).thenReturn(Optional.of(docRef));

        var result = useCase.execute(DATE);

        assertThat(result).containsExactly(Map.entry("Dmitry / Maria", "https://docs.google.com/doc2"));
        verify(notes).findDoc("_Notes/People/Maria");
    }

    @Test
    void groupMeeting_usesResolverPathAndFindsDoc() {
        var topics = List.of(new Topic("action items", List.of()));
        when(board.getMeetingsWithTopics()).thenReturn(List.of(
                new MeetingWithTopics("Meeting: Weekly Sync", topics)
        ));
        when(calendar.getMeetings(DATE)).thenReturn(List.of(
                new Meeting("Weekly Sync", List.of("a@x.com", "b@x.com", "c@x.com"))
        ));
        when(resolver.resolveDocName("Weekly Sync")).thenReturn("_Notes/Teams/Platform Team");
        var docRef = new DocRef("doc3", "https://docs.google.com/doc3");
        when(notes.findDoc("_Notes/Teams/Platform Team")).thenReturn(Optional.of(docRef));

        var result = useCase.execute(DATE);

        assertThat(result).containsExactly(Map.entry("Weekly Sync", "https://docs.google.com/doc3"));
        verify(resolver).resolveDocName("Weekly Sync");
        verify(notes).findDoc("_Notes/Teams/Platform Team");
        verify(notes).appendAgenda(docRef, DATE, "Weekly Sync", topics);
    }

    @Test
    void twoAttendeesButTitleNotOneOnOnePattern_treatedAsGroup() {
        when(board.getMeetingsWithTopics()).thenReturn(List.of(
                new MeetingWithTopics("Meeting: Standup", List.of())
        ));
        when(calendar.getMeetings(DATE)).thenReturn(List.of(
                new Meeting("Standup", List.of("a@x.com", "b@x.com"))
        ));
        when(resolver.resolveDocName("Standup")).thenThrow(new MissingDocMappingException("Standup"));

        var result = useCase.execute(DATE);

        assertThat(result).isEmpty();
        verify(resolver).resolveDocName("Standup");
        verifyNoInteractions(notes);
    }

    @Test
    void skipsAndContinuesWhenDocMappingMissing() {
        var topicItem = new Topic("item", List.of());
        var topicTopic = new Topic("topic", List.of());
        when(board.getMeetingsWithTopics()).thenReturn(List.of(
                new MeetingWithTopics("Meeting: Weekly Sync", List.of(topicItem)),
                new MeetingWithTopics("Meeting: Ivan / Dima", List.of(topicTopic))
        ));
        when(calendar.getMeetings(DATE)).thenReturn(List.of(
                new Meeting("Weekly Sync", List.of("a@x.com", "b@x.com", "c@x.com")),
                new Meeting("Ivan / Dima", List.of("ivan@x.com", "dima@x.com"))
        ));
        when(resolver.resolveDocName("Weekly Sync")).thenThrow(new MissingDocMappingException("Weekly Sync"));
        when(resolver.resolveDocName("Ivan / Dima")).thenThrow(new MissingDocMappingException("Ivan / Dima"));
        var docRef = new DocRef("doc4", "https://docs.google.com/doc4");
        when(notes.findDoc("_Notes/People/Ivan")).thenReturn(Optional.of(docRef));

        var result = useCase.execute(DATE);

        assertThat(result).containsExactly(Map.entry("Ivan / Dima", "https://docs.google.com/doc4"));
        verify(notes, never()).appendAgenda(any(), any(), any(), eq(List.of(topicItem)));
    }

    @Test
    void returnsEmptyWhenNoBoardCards() {
        when(board.getMeetingsWithTopics()).thenReturn(List.of());
        when(calendar.getMeetings(DATE)).thenReturn(List.of());

        var result = useCase.execute(DATE);

        assertThat(result).isEmpty();
        verifyNoInteractions(notes, resolver);
    }

    @Test
    void extractOtherPersonName_nameBeforeDima() {
        assertThat(useCase.extractOtherPersonName("Ivan / Dima")).isEqualTo("Ivan");
    }

    @Test
    void extractOtherPersonName_nameAfterDmitry() {
        assertThat(useCase.extractOtherPersonName("Dmitry / Maria")).isEqualTo("Maria");
    }

    @Test
    void extractOtherPersonName_dmitriiVariant() {
        assertThat(useCase.extractOtherPersonName("Dmitrii / Olga")).isEqualTo("Olga");
    }

    @Test
    void extractOtherPersonName_withTicketPrefix() {
        assertThat(useCase.extractOtherPersonName("ODM-12259. Mikhail / Dmitry")).isEqualTo("Mikhail");
    }

    @Test
    void extractOtherPersonName_withMilestoneSuffix() {
        assertThat(useCase.extractOtherPersonName("Ivan / Dmitry - milestone 3")).isEqualTo("Ivan");
    }

    @Test
    void extractOtherPersonName_withChatPrefix() {
        assertThat(useCase.extractOtherPersonName("Random chat 2 - Gleb / Dmitry")).isEqualTo("Gleb");
    }
}
