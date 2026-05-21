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

/**
 * Orchestrates the <em>create-agenda</em> phase (phase 2) of the meeting-preparation workflow.
 *
 * <h2>Purpose</h2>
 * <p>For each meeting on the Trello board's Meetings list, this use case locates the
 * corresponding notes document in Google Drive and appends a dated agenda section populated
 * with topic entries from the board.  It is intended to be run on the day of the meetings,
 * after {@code draft-plan} (phase 1) has already created the meeting and topic cards.</p>
 *
 * <h2>Input</h2>
 * <ul>
 *   <li>{@code date} – the calendar date for which to fetch meetings and append the agenda
 *       heading.  Typically "today", but can be any date when using {@code --date}.</li>
 * </ul>
 *
 * <h2>Output</h2>
 * <p>An ordered {@code Map<meetingTitle, documentUrl>} for every meeting that was successfully
 * processed (document found and agenda appended).  The order matches the card order on the board.</p>
 *
 * <h2>Meeting-type classification</h2>
 * <p>Each Trello card's title is stripped of the {@code "Meeting: "} prefix and compared
 * against a regex pattern {@code <Name> / Dima|Dmitry|Dmitrii} (both orderings).  If the
 * title matches <em>and</em> Google Calendar confirms at most two attendees, the meeting is
 * classified as {@code ONE_ON_ONE}; otherwise it is {@code GROUP}.</p>
 *
 * <h2>Drive path resolution</h2>
 * <ul>
 *   <li><strong>ONE_ON_ONE</strong> – path is built as {@code {notesDir}/People/{otherPersonName}},
 *       where {@code otherPersonName} is the non-Dima/Dmitry participant extracted from the title.</li>
 *   <li><strong>GROUP</strong> – path is resolved via {@link ManualLinkResolverPort}, which looks up
 *       the meeting title in the {@code docMappings} section of {@code settings.yaml}.
 *       If no entry exists, a {@link com.paulfrmbrn.adapter.out.mapping.MissingDocMappingException}
 *       is thrown, the meeting is skipped with a console message, and processing continues
 *       with the next meeting.</li>
 * </ul>
 *
 * <h2>Topic enrichment and agenda format</h2>
 * <p>Each topic card on the board carries zero or more Trello checklists, each with zero or
 * more checklist items.  The agenda section appended to Google Docs is formatted by
 * {@link AgendaFormatter} as plain text with the following rules:</p>
 * <ul>
 *   <li>The date is written as a {@code HEADING_1} line ({@code yyyy-MM-dd}).</li>
 *   <li>Each topic starts with {@code "> <topic name>"}.</li>
 *   <li>If the topic has <strong>no checklists</strong>: a {@code <notes></notes>} placeholder
 *       is added directly below the topic line.</li>
 *   <li>If there is exactly <strong>one checklist named "Checklist"</strong>: the {@code ">> Checklist"}
 *       header is omitted; only the last unchecked item and {@code <notes></notes>} block are written.</li>
 *   <li>Otherwise, each checklist is written as {@code ">> <checklist name>"} followed by
 *       the last unchecked item (or {@code "- no items"} if all items are complete) and a
 *       {@code <notes></notes>} block.</li>
 *   <li>"Last unchecked item" is the item with the highest {@code pos} value among all
 *       {@code incomplete} items in that checklist.</li>
 * </ul>
 * <pre>
 * 2026-04-15                     ← HEADING_1 in Google Docs
 * &gt; Deploy                       ← topic with no checklists
 * &lt;notes&gt;
 * &lt;/notes&gt;
 * &gt; Fix regression               ← topic with single "Checklist" checklist
 * - Write failing test
 * &lt;notes&gt;
 * &lt;/notes&gt;
 * &gt; Sprint planning              ← topic with named checklists
 * &gt;&gt; Prep
 * - Book room
 * &lt;notes&gt;
 * &lt;/notes&gt;
 * &gt;&gt; Goals
 * - no items
 * &lt;notes&gt;
 * &lt;/notes&gt;
 * </pre>
 *
 * <h2>Error handling</h2>
 * <ul>
 *   <li>If the Drive document is not found for a resolved path, a warning is logged and the
 *       meeting is silently skipped (no entry in the result map).</li>
 *   <li>Missing {@code docMappings} entries print a console hint and skip the meeting.</li>
 *   <li>I/O failures from ports propagate as runtime exceptions.</li>
 * </ul>
 */
public class PrepareMeetingNotes implements PrepareMeetingNotesUseCase {

    private static final Logger log = LoggerFactory.getLogger(PrepareMeetingNotes.class);

    // Matches "Name / Dima", "Name/Dima", "Prefix Name / Dima", "Name / Dima suffix", etc.
    // Spaces around "/" are optional. Optional prefix (e.g. ticket id "ODM-12259. ") and
    // suffix (e.g. " - milestone 3", ": Databricks prep call") are allowed.
    // [^/] in the prefix part ensures titles with multiple "/" (e.g. "A / B / Dmitry") are not matched.
    private static final Pattern ONE_ON_ONE_PATTERN =
            Pattern.compile("^(?:[^/]*?\\s)?([A-Za-z]+)\\s*/\\s*(Dima|Dmitry|Dmitrii)(?:[:\\s].*)?$" +
                    "|^(?:[^/]*?\\s)?(Dima|Dmitry|Dmitrii)\\s*/\\s*([A-Za-z]+)(?:[:\\s].*)?$");

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
                    result.put(eventTitle, null);
                    continue;
                }
            }

            Optional<DocRef> docRef = notes.findDoc(drivePath);

            if (docRef.isEmpty()) {
                log.warn("No notes document found for '{}', skipping agenda append", eventTitle);
                result.put(eventTitle, null);
                continue;
            }

            notes.appendAgenda(docRef.get(), date, eventTitle, meetingCard.topics());
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
