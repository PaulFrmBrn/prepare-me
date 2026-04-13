package com.paulfrmbrn.adapter.out.mapping;

public class MissingDocMappingException extends RuntimeException {

    private final String meetingTitle;

    public MissingDocMappingException(String meetingTitle) {
        super("No doc mapping for '" + meetingTitle + "'. "
                + "Add it to the docMappings section in settings.yaml:\n"
                + "  docMappings:\n"
                + "    \"" + meetingTitle + "\": <doc name>");
        this.meetingTitle = meetingTitle;
    }

    public String getMeetingTitle() {
        return meetingTitle;
    }
}
