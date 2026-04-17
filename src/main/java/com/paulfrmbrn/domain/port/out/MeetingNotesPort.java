package com.paulfrmbrn.domain.port.out;

import com.paulfrmbrn.domain.model.DocRef;
import com.paulfrmbrn.domain.model.Topic;
import com.paulfrmbrn.domain.model.TopicContent;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MeetingNotesPort {
    Optional<DocRef> findDoc(String drivePath);
    void appendAgenda(DocRef doc, LocalDate date, String meetingName, List<Topic> topics);
    List<TopicContent> readTopicNotes(DocRef doc, LocalDate date, String meetingName);
}
