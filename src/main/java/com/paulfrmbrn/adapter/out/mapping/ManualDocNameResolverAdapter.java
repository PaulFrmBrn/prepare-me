package com.paulfrmbrn.adapter.out.mapping;

import com.paulfrmbrn.domain.port.out.ManualLinkResolverPort;
import com.paulfrmbrn.infrastructure.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Resolves notes document names from {@code doc-mappings.yaml}.
 *
 * <p>An exact {@code titles:} entry wins; otherwise the longest matching {@code prefixes:}
 * entry is used, so a whole family of team meetings ({@code "AIT: ..."}) maps to one document
 * without listing every title.</p>
 */
public class ManualDocNameResolverAdapter implements ManualLinkResolverPort {

    private static final Logger log = LoggerFactory.getLogger(ManualDocNameResolverAdapter.class);

    private final Path mappingsFile;

    public ManualDocNameResolverAdapter(Path mappingsFile) {
        this.mappingsFile = mappingsFile;
    }

    @Override
    public String resolveDocName(String meetingTitle) {
        Settings.DocMappings mappings = loadMappings();

        String docName = mappings.titles.get(meetingTitle);
        if (docName != null) {
            log.debug("Found doc mapping for '{}': '{}'", meetingTitle, docName);
            return docName;
        }

        String prefix = longestMatchingPrefix(mappings.prefixes, meetingTitle);
        if (prefix != null) {
            docName = mappings.prefixes.get(prefix);
            log.debug("Found doc mapping for '{}' by prefix '{}': '{}'", meetingTitle, prefix, docName);
            return docName;
        }

        throw new MissingDocMappingException(meetingTitle);
    }

    private static String longestMatchingPrefix(Map<String, String> prefixes, String meetingTitle) {
        String best = null;
        for (String prefix : prefixes.keySet()) {
            if (prefix.isEmpty() || !meetingTitle.startsWith(prefix)) continue;
            if (best == null || prefix.length() > best.length()) best = prefix;
        }
        return best;
    }

    private Settings.DocMappings loadMappings() {
        try {
            return Settings.loadDocMappings(mappingsFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load doc mappings from " + mappingsFile + ": " + e.getMessage(), e);
        }
    }
}
