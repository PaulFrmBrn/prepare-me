package com.paulfrmbrn.adapter.out.mapping;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.paulfrmbrn.domain.port.out.ManualLinkResolverPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class ManualDocNameResolverAdapter implements ManualLinkResolverPort {

    private static final Logger log = LoggerFactory.getLogger(ManualDocNameResolverAdapter.class);

    private final Path mappingsFile;

    public ManualDocNameResolverAdapter(Path mappingsFile) {
        this.mappingsFile = mappingsFile;
    }

    @Override
    public String resolveDocName(String meetingTitle) {
        Map<String, String> mappings = loadMappings();
        String docName = mappings.get(meetingTitle);
        if (docName != null) {
            log.debug("Found doc mapping for '{}': '{}'", meetingTitle, docName);
            return docName;
        }
        throw new MissingDocMappingException(meetingTitle);
    }

    private Map<String, String> loadMappings() {
        if (!mappingsFile.toFile().exists()) return new LinkedHashMap<>();
        try {
            return new ObjectMapper(new YAMLFactory()).readValue(mappingsFile.toFile(), new TypeReference<>() {});
        } catch (IOException e) {
            throw new RuntimeException("Failed to load doc mappings from " + mappingsFile + ": " + e.getMessage(), e);
        }
    }
}
