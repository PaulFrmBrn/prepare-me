package com.paulfrmbrn.adapter.out.mapping;

public class MissingDocMappingException extends RuntimeException {

    private final String meetingTitle;

    public MissingDocMappingException(String meetingTitle) {
        super("No doc mapping for '" + meetingTitle + "'. "
                + "Add it to doc-mappings.yaml:\n"
                + "  titles:\n"
                + "    \"" + meetingTitle + "\": <doc name>\n"
                + "or map the whole family of meetings by title prefix:\n"
                + "  prefixes:\n"
                + "    \"<prefix>:\": <doc name>");
        this.meetingTitle = meetingTitle;
    }

    public String getMeetingTitle() {
        return meetingTitle;
    }
}
