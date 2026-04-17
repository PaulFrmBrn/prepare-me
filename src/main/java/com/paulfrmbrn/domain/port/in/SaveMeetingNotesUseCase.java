package com.paulfrmbrn.domain.port.in;

import java.time.LocalDate;
import java.util.Map;

public interface SaveMeetingNotesUseCase {
    /** Returns an ordered map of meeting name → number of topic comments posted. */
    Map<String, Integer> execute(LocalDate date);
}
