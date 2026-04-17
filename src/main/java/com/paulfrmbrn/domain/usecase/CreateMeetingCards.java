package com.paulfrmbrn.domain.usecase;

import com.paulfrmbrn.domain.model.Meeting;
import com.paulfrmbrn.domain.port.in.CreateMeetingCardsUseCase;
import com.paulfrmbrn.domain.port.out.CalendarPort;
import com.paulfrmbrn.domain.port.out.MeetingBoardPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public class CreateMeetingCards implements CreateMeetingCardsUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateMeetingCards.class);

    private final CalendarPort calendar;
    private final MeetingBoardPort board;
    private final Set<String> excluded;

    public CreateMeetingCards(CalendarPort calendar, MeetingBoardPort board, Set<String> excluded) {
        this.calendar = calendar;
        this.board = board;
        this.excluded = excluded;
    }

    @Override
    public List<String> execute(LocalDate date) {
        var meetings = calendar.getMeetings(date);
        log.info("Calendar events for {}: {}", date, meetings.stream().map(Meeting::name).toList());

        var cardNames = meetings.stream()
                .filter(m -> !excluded.contains(m.name()))
                .map(m -> "Meeting: " + m.name())
                .toList();

        cardNames.forEach(board::createCard);
        return cardNames;
    }
}
