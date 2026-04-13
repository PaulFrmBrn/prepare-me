package com.paulfrmbrn.domain.port.out;

public interface MeetingBoardPort {
    boolean isMeetingListEmpty();
    void createCard(String name);
}
