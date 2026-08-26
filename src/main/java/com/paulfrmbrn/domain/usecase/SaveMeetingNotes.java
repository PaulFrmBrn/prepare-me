package com.paulfrmbrn.domain.usecase;

import com.paulfrmbrn.adapter.out.mapping.MissingDocMappingException;
import com.paulfrmbrn.domain.model.DocRef;
import com.paulfrmbrn.domain.model.Meeting;
import com.paulfrmbrn.domain.model.MeetingType;
import com.paulfrmbrn.domain.model.TopicContent;
import com.paulfrmbrn.domain.port.in.SaveMeetingNotesUseCase;
import com.paulfrmbrn.domain.port.out.CalendarPort;
import com.paulfrmbrn.domain.port.out.ManualLinkResolverPort;
import com.paulfrmbrn.domain.port.out.MeetingBoardPort;
import com.paulfrmbrn.domain.port.out.MeetingNotesPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Orchestrates the <em>save-notes</em> phase (phase 4) of the meeting-preparation workflow.
 *
 * <p>For each meeting on the Trello board, locates the notes document, reads the topic sections
 * that were filled in after Create Agenda, and posts each topic's body text as a comment on the
 * corresponding Trello topic card.</p>
 *
 * <p>Doc-finding logic (1-1 detection, drive path resolution) mirrors {@link PrepareMeetingNotes}.</p>
 */
public class SaveMeetingNotes implements SaveMeetingNotesUseCase {

    private static final Logger log = LoggerFactory.getLogger(SaveMeetingNotes.class);

    private static final Pattern ONE_ON_ONE_PATTERN =
            Pattern.compile("^([^/]+)\\s*/\\s*(Dima|Dmitry|Dmitrii)(?:[:\\s].*)?$" +
                    "|^(Dima|Dmitry|Dmitrii)\\s*/\\s*([^/:]+)(?:[:\\s].*)?$");

    private final MeetingBoardPort board;
    private final CalendarPort calendar;
    private final MeetingNotesPort notes;
    private final ManualLinkResolverPort resolver;
    private final String notesDir;

    public SaveMeetingNotes(MeetingBoardPort board, CalendarPort calendar,
                            MeetingNotesPort notes, ManualLinkResolverPort resolver,
                            String notesDir) {
        this.board = board;
        this.calendar = calendar;
        this.notes = notes;
        this.resolver = resolver;
        this.notesDir = notesDir;
    }

    @Override
    public Map<String, Integer> execute(LocalDate date) {
        var meetingsWithTopics = board.getMeetingsWithTopics();
        log.info("Meetings with topics from board: {}", meetingsWithTopics.stream()
                .map(m -> m.name() + " (" + m.topics().size() + " topics)").toList());

        Map<String, Meeting> calendarByTitle = calendar.getMeetings(date).stream()
                .collect(Collectors.toMap(Meeting::name, Function.identity(), (a, _) -> a));

        Map<String, Integer> result = new LinkedHashMap<>();

        for (var meetingCard : meetingsWithTopics) {
            String cardName = meetingCard.name();
            String eventTitle = (cardName.startsWith("Meeting: ")
                    ? cardName.substring("Meeting: ".length())
                    : cardName).strip();

            Meeting calEntry = calendarByTitle.get(eventTitle);
            MeetingType type = detectType(eventTitle, calEntry);
            log.debug("Meeting '{}' detected as {}", eventTitle, type);

            String drivePath;
            try {
                drivePath = resolver.resolveDocName(eventTitle);
            } catch (MissingDocMappingException e) {
                if (type == MeetingType.ONE_ON_ONE) {
                    String personName = extractOtherPersonName(eventTitle);
                    drivePath = notesDir + "/People/" + personName;
                } else {
                    log.warn("No doc mapping for '{}', skipping", eventTitle);
                    result.put(eventTitle, -1);
                    continue;
                }
            }

            Optional<DocRef> docRef = notes.findDoc(drivePath);
            if (docRef.isEmpty()) {
                log.warn("No notes document found for '{}', skipping", eventTitle);
                result.put(eventTitle, -1);
                continue;
            }

            List<TopicContent> topicContents = notes.readTopicNotes(docRef.get(), date, eventTitle);

            Map<String, String> topicIdByName = meetingCard.topics().stream()
                    .collect(Collectors.toMap(t -> t.name(), t -> t.id(), (a, _) -> a));
            log.debug("Trello topics for '{}': {}", eventTitle, topicIdByName.keySet());

            int postedCount = 0;
            for (TopicContent tc : topicContents) {
                String topicId = topicIdByName.get(tc.topicName());
                if (topicId == null || topicId.isBlank()) {
                    log.warn("No Trello card found for topic '{}' in meeting '{}', skipping. Available topics: {}",
                            tc.topicName(), eventTitle, topicIdByName.keySet());
                    continue;
                }
                String comment = date + " " + eventTitle + "\n\n" + tc.bodyText() + "\n\n" + docRef.get().url();
                board.addTopicComment(topicId, comment);
                postedCount++;
            }

            result.put(eventTitle, postedCount);
        }

        return result;
    }

    private MeetingType detectType(String eventTitle, Meeting calEntry) {
        if (!ONE_ON_ONE_PATTERN.matcher(eventTitle).matches()) {
            return MeetingType.GROUP;
        }
        if (calEntry != null && calEntry.attendees().size() > 2) {
            return MeetingType.GROUP;
        }
        return MeetingType.ONE_ON_ONE;
    }

    String extractOtherPersonName(String eventTitle) {
        var matcher = ONE_ON_ONE_PATTERN.matcher(eventTitle);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a 1-1 title: " + eventTitle);
        }
        String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(4);
        name = name.trim();
        // Strip ticket prefix like "ODM-12259. " before the person name
        name = name.replaceFirst("^[A-Z]+-\\d+\\.\\s*", "");
        return name;
    }
}
