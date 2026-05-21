package com.paulfrmbrn.domain.port.in;

import java.time.LocalDate;
import java.util.Map;

public interface PrepareMeetingNotesUseCase {
    /**
     * Returns an ordered map of meeting name → document URL.
     * A null value means the meeting was skipped (no notes document found or no doc mapping).
     */
    Map<String, String> execute(LocalDate date);
}
