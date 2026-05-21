package com.paulfrmbrn.domain.port.in;

import java.time.LocalDate;
import java.util.Map;

public interface SaveMeetingNotesUseCase {
    /**
     * Returns an ordered map of meeting name → number of topic comments posted.
     * A value of -1 means the meeting was skipped (no notes document found or no doc mapping).
     */
    Map<String, Integer> execute(LocalDate date);
}
