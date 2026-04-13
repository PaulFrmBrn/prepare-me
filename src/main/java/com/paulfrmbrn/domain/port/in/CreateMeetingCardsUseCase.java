package com.paulfrmbrn.domain.port.in;

import java.time.LocalDate;
import java.util.List;

public interface CreateMeetingCardsUseCase {
    /** Returns the names of the cards created (e.g. "Meeting: Team Sync"). */
    List<String> execute(LocalDate date);
}
