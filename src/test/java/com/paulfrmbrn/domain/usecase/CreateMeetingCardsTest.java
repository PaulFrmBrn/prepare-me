package com.paulfrmbrn.domain.usecase;

import com.paulfrmbrn.domain.model.Meeting;

import java.util.List;
import com.paulfrmbrn.domain.port.out.CalendarPort;
import com.paulfrmbrn.domain.port.out.MeetingBoardPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateMeetingCardsTest {

    static final LocalDate DATE = LocalDate.of(2026, 4, 14);

    @Mock CalendarPort calendar;
    @Mock MeetingBoardPort board;

    CreateMeetingCards useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateMeetingCards(calendar, board, Set.of("NO MEETINGS, PLEASE", "Lunch"));
    }

    @Test
    void createsMeetingCardsWithPrefix() {
        when(calendar.getMeetings(DATE)).thenReturn(List.of(
                new Meeting("Team Sync", List.of()),
                new Meeting("1:1 with Ivan", List.of())
        ));

        var result = useCase.execute(DATE);

        assertThat(result).containsExactly("Meeting: Team Sync", "Meeting: 1:1 with Ivan");
        verify(board).createCard("Meeting: Team Sync");
        verify(board).createCard("Meeting: 1:1 with Ivan");
    }

    @Test
    void excludesLunchAndNoMeetingsEvents() {
        when(calendar.getMeetings(DATE)).thenReturn(List.of(
                new Meeting("Team Sync", List.of()),
                new Meeting("Lunch", List.of()),
                new Meeting("NO MEETINGS, PLEASE", List.of()),
                new Meeting("1:1 with Ivan", List.of())
        ));

        var result = useCase.execute(DATE);

        assertThat(result).containsExactly("Meeting: Team Sync", "Meeting: 1:1 with Ivan");
        verify(board, never()).createCard("Lunch");
        verify(board, never()).createCard("NO MEETINGS, PLEASE");
    }

    @Test
    void returnsEmptyListWhenCalendarIsEmpty() {
        when(calendar.getMeetings(DATE)).thenReturn(List.of());

        var result = useCase.execute(DATE);

        assertThat(result).isEmpty();
        verifyNoInteractions(board);
    }

    @Test
    void returnsEmptyListWhenAllEventsAreExcluded() {
        when(calendar.getMeetings(DATE)).thenReturn(List.of(
                new Meeting("Lunch", List.of()),
                new Meeting("NO MEETINGS, PLEASE", List.of())
        ));

        var result = useCase.execute(DATE);

        assertThat(result).isEmpty();
        verifyNoInteractions(board);
    }
}
