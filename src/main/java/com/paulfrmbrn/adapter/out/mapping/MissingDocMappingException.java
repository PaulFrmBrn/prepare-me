package com.paulfrmbrn.adapter.out.mapping;

public class MissingDocMappingException extends RuntimeException {

    private final String meetingTitle;

    public MissingDocMappingException(String meetingTitle) {
        super("No doc mapping for '" + meetingTitle + "'. "
                + "Add it to doc-mappings.yaml:\n"
                + "  \"" + meetingTitle + "\": <doc name>");
        this.meetingTitle = meetingTitle;
    }

    public String getMeetingTitle() {
        return meetingTitle;
    }
}
