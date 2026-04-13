package com.paulfrmbrn.domain.usecase;

import com.paulfrmbrn.adapter.out.mapping.MissingDocMappingException;
import com.paulfrmbrn.domain.model.DocRef;
import com.paulfrmbrn.domain.model.Meeting;
import com.paulfrmbrn.domain.model.MeetingType;
import com.paulfrmbrn.domain.port.in.PrepareMeetingNotesUseCase;
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

public class PrepareMeetingNotes implements PrepareMeetingNotesUseCase {

    private static final Logger log = LoggerFactory.getLogger(PrepareMeetingNotes.class);

    // Matches "Name / Dima", "Dima / Name", "Name / Dmitry", etc.
    private static final Pattern ONE_ON_ONE_PATTERN =
            Pattern.compile("^(.+?) / (Dima|Dmitry|Dmitrii)$|^(Dima|Dmitry|Dmitrii) / (.+?)$");

    private final MeetingBoardPort board;
    private final CalendarPort calendar;
    private final MeetingNotesPort notes;
    private final ManualLinkResolverPort resolver;
    private final String notesDir;

    public PrepareMeetingNotes(MeetingBoardPort board, CalendarPort calendar,
                               MeetingNotesPort notes, ManualLinkResolverPort resolver,
                               String notesDir) {
        this.board = board;
        this.calendar = calendar;
        this.notes = notes;
        this.resolver = resolver;
        this.notesDir = notesDir;
    }

    @Override
    public Map<String, String> execute(LocalDate date) {
        var meetingsWithTopics = board.getMeetingsWithTopics();
        log.info("Meetings with topics from board: {}", meetingsWithTopics.stream()
                .map(m -> m.name() + " (" + m.topics().size() + " topics)").toList());

        // Build a name→Meeting map for attendee lookup (strip "Meeting: " prefix)
        Map<String, Meeting> calendarByTitle = calendar.getMeetings(date).stream()
                .collect(Collectors.toMap(Meeting::name, Function.identity(), (a, _) -> a));

        Map<String, String> result = new LinkedHashMap<>();

        for (var meetingCard : meetingsWithTopics) {
            String cardName = meetingCard.name();
            // Card names are "Meeting: <event title>"
            String eventTitle = cardName.startsWith("Meeting: ")
                    ? cardName.substring("Meeting: ".length())
                    : cardName;

            Meeting calEntry = calendarByTitle.get(eventTitle);
            MeetingType type = detectType(eventTitle, calEntry);
            log.debug("Meeting '{}' detected as {}", eventTitle, type);

            String drivePath;
            if (type == MeetingType.ONE_ON_ONE) {
                String personName = extractOtherPersonName(eventTitle);
                drivePath = notesDir + "/People/" + personName;
            } else {
                try {
                    drivePath = resolver.resolveDocName(eventTitle);
                } catch (MissingDocMappingException e) {
                    System.out.println("Skipped: " + e.getMessage());
                    continue;
                }
            }

            Optional<DocRef> docRef = notes.findDoc(drivePath);

            if (docRef.isEmpty()) {
                log.warn("No notes document found for '{}', skipping agenda append", eventTitle);
                continue;
            }

            notes.appendAgenda(docRef.get(), date, meetingCard.topics());
            result.put(eventTitle, docRef.get().url());
        }

        return result;
    }

    private MeetingType detectType(String eventTitle, Meeting calEntry) {
        if (!ONE_ON_ONE_PATTERN.matcher(eventTitle).matches()) {
            return MeetingType.GROUP;
        }
        // Title matches 1-1 pattern; only override to GROUP if Calendar confirms > 2 attendees
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
        // Group 1: "Name / Dima" — other name is group 1
        // Group 4: "Dima / Name" — other name is group 4
        String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(4);
        return name.trim();
    }
}
