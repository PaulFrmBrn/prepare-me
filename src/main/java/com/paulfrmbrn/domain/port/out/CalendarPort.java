package com.paulfrmbrn.domain.port.out;

import com.paulfrmbrn.domain.model.Meeting;

import java.time.LocalDate;
import java.util.List;

public interface CalendarPort {
    List<Meeting> getMeetings(LocalDate date);
}
