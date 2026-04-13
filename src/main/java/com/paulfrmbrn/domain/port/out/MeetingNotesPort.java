package com.paulfrmbrn.domain.port.out;

import com.paulfrmbrn.domain.model.DocRef;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MeetingNotesPort {
    Optional<DocRef> findDoc(String drivePath);
    void appendAgenda(DocRef doc, LocalDate date, List<String> topics);
}
