package com.paulfrmbrn.domain.port.in;

import java.time.LocalDate;
import java.util.Map;

public interface PrepareMeetingNotesUseCase {
    /** Returns an ordered map of meeting name → document URL. */
    Map<String, String> execute(LocalDate date);
}
