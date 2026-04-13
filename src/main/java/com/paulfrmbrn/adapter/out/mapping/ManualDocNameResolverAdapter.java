package com.paulfrmbrn.adapter.out.mapping;

import com.paulfrmbrn.domain.port.out.ManualLinkResolverPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ManualDocNameResolverAdapter implements ManualLinkResolverPort {

    private static final Logger log = LoggerFactory.getLogger(ManualDocNameResolverAdapter.class);

    private final Map<String, String> mappings;

    public ManualDocNameResolverAdapter(Map<String, String> mappings) {
        this.mappings = mappings;
    }

    @Override
    public String resolveDocName(String meetingTitle) {
        String docName = mappings.get(meetingTitle);
        if (docName != null) {
            log.debug("Found doc mapping for '{}': '{}'", meetingTitle, docName);
            return docName;
        }
        throw new MissingDocMappingException(meetingTitle);
    }
}
