package com.paulfrmbrn.domain.port.out;

import com.paulfrmbrn.domain.model.MeetingWithTopics;

import java.util.List;

public interface MeetingBoardPort {
    void createCard(String name);
    List<MeetingWithTopics> getMeetingsWithTopics();
}
