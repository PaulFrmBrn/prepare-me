package com.paulfrmbrn.domain.usecase;

import com.paulfrmbrn.domain.port.in.CreateMeetingCardsUseCase;
import com.paulfrmbrn.domain.port.out.CalendarPort;
import com.paulfrmbrn.domain.port.out.MeetingBoardPort;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public class CreateMeetingCards implements CreateMeetingCardsUseCase {

    private static final Set<String> EXCLUDED = Set.of("NO MEETINGS, PLEASE", "Lunch");

    private final CalendarPort calendar;
    private final MeetingBoardPort board;

    public CreateMeetingCards(CalendarPort calendar, MeetingBoardPort board) {
        this.calendar = calendar;
        this.board = board;
    }

    @Override
    public List<String> execute(LocalDate date) {
        if (!board.isMeetingListEmpty()) {
            throw new IllegalStateException("Meetings list is not empty — clear it before running draft-plan");
        }
        var cardNames = calendar.getMeetings(date).stream()
                .filter(m -> !EXCLUDED.contains(m.name()))
                .map(m -> "Meeting: " + m.name())
                .toList();

        cardNames.forEach(board::createCard);
        return cardNames;
    }
}
