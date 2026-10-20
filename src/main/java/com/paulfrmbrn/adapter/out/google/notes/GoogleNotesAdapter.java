package com.paulfrmbrn.adapter.out.google.notes;

import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.model.BatchUpdateDocumentRequest;
import com.google.api.services.docs.v1.model.CreateNamedRangeRequest;
import com.google.api.services.docs.v1.model.DateElementProperties;
import com.google.api.services.docs.v1.model.Document;
import com.google.api.services.docs.v1.model.InsertDateRequest;
import com.google.api.services.docs.v1.model.InsertTextRequest;
import com.google.api.services.docs.v1.model.Location;
import com.google.api.services.docs.v1.model.NamedRanges;
import com.google.api.services.docs.v1.model.Bullet;
import com.google.api.services.docs.v1.model.Paragraph;
import com.google.api.services.docs.v1.model.ParagraphStyle;
import com.google.api.services.docs.v1.model.Range;
import com.google.api.services.docs.v1.model.Request;
import com.google.api.services.docs.v1.model.StructuralElement;
import com.google.api.services.docs.v1.model.UpdateParagraphStyleRequest;
import com.google.api.services.drive.Drive;
import com.paulfrmbrn.adapter.out.google.auth.GoogleAuthProvider;
import com.paulfrmbrn.domain.model.DocRef;
import com.paulfrmbrn.domain.model.Topic;
import com.paulfrmbrn.domain.model.TopicContent;
import com.paulfrmbrn.domain.port.out.MeetingNotesPort;
import com.paulfrmbrn.domain.usecase.AgendaFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class GoogleNotesAdapter implements MeetingNotesPort {

    private static final Logger log = LoggerFactory.getLogger(GoogleNotesAdapter.class);

    /**
     * Marks an appended agenda entry so a later run can recognise it without reading the notes.
     * The Docs API has no range read, so the only way to keep the idempotency check cheap on a
     * long-lived document is to check something whose size tracks the number of agenda entries
     * rather than the length of the document.
     */
    private static final String AGENDA_RANGE_PREFIX = "prepare-me:agenda:";

    /** Docs caps named range names at 256 characters. */
    private static final int MAX_RANGE_NAME_LENGTH = 256;

    /**
     * Locator read: the agenda markers plus the paragraph end indices, and nothing else. This is
     * the only read on the append path and it transfers no document text.
     */
    private static final String LOCATOR_FIELDS = "namedRanges,body.content(endIndex)";

    /**
     * Full-text mask, used by {@link #readTopicNotes} and by the one-time legacy scan. Only the
     * paragraph text, named style and bullet level are used. Without it the API returns the full
     * style metadata of every text run, which is an order of magnitude more data again.
     */
    private static final String DOCUMENT_FIELDS =
            "body.content("
            + "endIndex,"
            + "paragraph("
            + "bullet(nestingLevel),"
            + "paragraphStyle(namedStyleType),"
            + "elements(textRun(content),richLink(richLinkProperties(title,uri,mimeType)))"
            + "))";

    // Supported mime types: native Google Docs and uploaded .docx files
    private static final String MIME_QUERY =
            "(mimeType = 'application/vnd.google-apps.document'"
            + " or mimeType = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document')";

    private final GoogleAuthProvider auth;

    public GoogleNotesAdapter(GoogleAuthProvider auth) {
        this.auth = auth;
    }

    /**
     * Finds a document by Drive path. The path is slash-separated where all but the
     * last segment are folders and the last segment is the file name (extension optional).
     * Example: "_Notes/People/Ivan" or "_Notes/Teams/ODM.docx"
     */
    @Override
    public Optional<DocRef> findDoc(String drivePath) {
        try {
            var drive = buildDrive();
            String[] parts = drivePath.split("/");
            String fileName = stripExtension(parts[parts.length - 1]);
            String[] folderParts = java.util.Arrays.copyOf(parts, parts.length - 1);

            String folderId = navigateFolders(drive, folderParts);
            if (folderId == null) {
                log.warn("Drive folder path '{}' not found", String.join("/", folderParts));
                return Optional.empty();
            }

            // Try exact match on both "Name.docx" and "Name" (extension-stripped)
            String originalName = parts[parts.length - 1].replace("'", "\\'");
            String nameClause = fileName.equals(originalName)
                    ? "name = '" + fileName + "'"
                    : "(name = '" + originalName + "' or name = '" + fileName + "')";
            String query = nameClause
                    + " and '" + folderId + "' in parents"
                    + " and " + MIME_QUERY
                    + " and trashed = false";

            var files = drive.files().list()
                    .setQ(query)
                    .setFields("files(id, name, webViewLink, mimeType)")
                    .execute()
                    .getFiles();

            if (files == null || files.isEmpty()) {
                log.warn("No document matching '{}' found in '{}'", fileName,
                        String.join("/", folderParts));
                return Optional.empty();
            }

            var file = files.get(0);
            log.debug("Found document '{}' (id={}, type={}) for path '{}'",
                    file.getName(), file.getId(), file.getMimeType(), drivePath);
            return Optional.of(new DocRef(file.getId(), file.getWebViewLink()));

        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("Failed to find document at '" + drivePath + "': " + e.getMessage(), e);
        }
    }

    @Override
    public void appendAgenda(DocRef doc, LocalDate date, String meetingName, List<Topic> topics) {
        try {
            var docs = buildDocs();
            String rangeName = agendaRangeName(meetingName, date);

            var locator = docs.documents().get(doc.id()).setFields(LOCATOR_FIELDS).execute();
            Map<String, NamedRanges> markers = locator.getNamedRanges();

            if (agendaMarked(markers, rangeName)) {
                log.info("Agenda for '{}' on {} already exists in document {}, skipping", meetingName, date, doc.id());
                return;
            }

            List<StructuralElement> content = locator.getBody().getContent();
            int endIndex = content.stream()
                    .mapToInt(e -> e.getEndIndex() != null ? e.getEndIndex() : 0)
                    .max()
                    .orElse(1);
            int insertAt = Math.max(1, endIndex - 1);

            // Entries appended before marker tracking existed carry no named range, so fall back to
            // the full-text scan. This costs one expensive read per document, once: the append below
            // leaves a marker behind and every later run takes the cheap path above.
            if (!hasAgendaMarker(markers)) {
                log.debug("No agenda markers in document {}, falling back to full-text scan", doc.id());
                Document full;
                try {
                    full = docs.documents().get(doc.id()).setFields(DOCUMENT_FIELDS).execute();
                } catch (SocketTimeoutException e) {
                    throw oversizedDocument("Scanning", doc, endIndex, content.size(), e);
                }
                if (agendaEntryExists(full, meetingName, date)) {
                    log.info("Agenda for '{}' on {} already exists in document {}, skipping", meetingName, date, doc.id());
                    return;
                }
            }

            // Text: \n[space]{meetingName}\n{topics}
            // The leading space is the separator between the date chip and the meeting name.
            String text = AgendaFormatter.format(meetingName, topics);

            List<Request> requests = new ArrayList<>();
            // 1. Insert the agenda text
            requests.add(new Request().setInsertText(
                    new InsertTextRequest()
                            .setText(text)
                            .setLocation(new Location().setIndex(insertAt))
            ));
            // 2. Apply HEADING_1 to the meeting name paragraph (space + meetingName + \n)
            //    Range uses pre-chip indices; chip is inserted last so indices here are still valid.
            requests.add(new Request().setUpdateParagraphStyle(
                    new UpdateParagraphStyleRequest()
                            .setRange(new Range()
                                    .setStartIndex(insertAt + 1)
                                    .setEndIndex(insertAt + meetingName.length() + 3))
                            .setParagraphStyle(new ParagraphStyle().setNamedStyleType("HEADING_1"))
                            .setFields("namedStyleType")
            ));
            // 3. Apply HEADING_2/3 to topic and checklist lines
            requests.addAll(buildHeadingStyleRequests(text, insertAt));
            // 4. Mark the heading so the next run recognises this entry without reading the notes.
            //    Uses pre-chip indices like request 2; the chip insertion shifts the range with it.
            requests.add(new Request().setCreateNamedRange(
                    new CreateNamedRangeRequest()
                            .setName(rangeName)
                            .setRange(new Range()
                                    .setStartIndex(insertAt + 1)
                                    .setEndIndex(insertAt + meetingName.length() + 3))
            ));
            // 5. Insert date chip at insertAt+1 (before the space) — done last to avoid
            //    shifting the indices used in requests 2, 3 and 4
            requests.add(new Request().setInsertDate(
                    new InsertDateRequest()
                            .setLocation(new Location().setIndex(insertAt + 1))
                            .setDateElementProperties(new DateElementProperties()
                                    .setDateFormat("DATE_FORMAT_ISO8601")
                                    .setTimestamp(date.atStartOfDay(ZoneOffset.UTC)
                                            .format(java.time.format.DateTimeFormatter.ISO_INSTANT)))
            ));

            docs.documents().batchUpdate(doc.id(),
                    new BatchUpdateDocumentRequest().setRequests(requests)).execute();

            log.debug("Appended agenda for {} ({}) to document {}", meetingName, date, doc.id());

        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("Failed to append agenda to document '" + doc.id() + "': " + e.getMessage(), e);
        }
    }

    /** Name of the marker identifying one meeting's agenda entry for one date. */
    static String agendaRangeName(String meetingName, LocalDate date) {
        String name = AGENDA_RANGE_PREFIX + date + ":" + meetingName;
        return name.length() <= MAX_RANGE_NAME_LENGTH ? name : name.substring(0, MAX_RANGE_NAME_LENGTH);
    }

    /**
     * True if this document still carries a marker covering live content for this entry.
     *
     * <p>Google's documentation states only that named range indexes track edits; it does not say
     * what survives when a person deletes the content a range covers. An emptied range is therefore
     * treated as absent, so that manually deleting an agenda entry makes the next run re-append it
     * under either behaviour.</p>
     */
    static boolean agendaMarked(Map<String, NamedRanges> namedRanges, String rangeName) {
        if (namedRanges == null) return false;
        NamedRanges named = namedRanges.get(rangeName);
        if (named == null || named.getNamedRanges() == null) return false;
        return named.getNamedRanges().stream()
                .filter(r -> r.getRanges() != null)
                .flatMap(r -> r.getRanges().stream())
                .anyMatch(r -> r.getStartIndex() != null && r.getEndIndex() != null
                        && r.getEndIndex() > r.getStartIndex());
    }

    /** True if this document has been appended to since marker tracking was introduced. */
    static boolean hasAgendaMarker(Map<String, NamedRanges> namedRanges) {
        return namedRanges != null
                && namedRanges.keySet().stream().anyMatch(n -> n.startsWith(AGENDA_RANGE_PREFIX));
    }

    /**
     * Cheap size probe for error reporting: paragraph end indices only, no document text.
     * Returns {@code null} if even this fails, in which case the message omits the numbers.
     */
    private static int[] probeSize(Docs docs, DocRef doc) {
        try {
            var locator = docs.documents().get(doc.id()).setFields("body.content(endIndex)").execute();
            List<StructuralElement> content = locator.getBody().getContent();
            if (content == null) return null;
            int chars = content.stream()
                    .mapToInt(e -> e.getEndIndex() != null ? e.getEndIndex() : 0)
                    .max()
                    .orElse(0);
            return new int[]{chars, content.size()};
        } catch (Exception e) {
            log.debug("Size probe for document {} failed: {}", doc.id(), e.toString());
            return null;
        }
    }

    private static RuntimeException oversizedDocument(
            String action, DocRef doc, int[] size, SocketTimeoutException cause) {
        return size == null
                ? oversizedDocument(action, doc, -1, -1, cause)
                : oversizedDocument(action, doc, size[0], size[1], cause);
    }

    /**
     * A read timeout on a notes document is almost always the document having outgrown a single
     * Docs API response, so say that outright instead of surfacing a bare "Read timed out".
     */
    private static RuntimeException oversizedDocument(
            String action, DocRef doc, int characters, int elements, SocketTimeoutException cause) {
        String size = characters < 0
                ? "the document is too large to read in one response"
                : String.format("the document holds ~%,d characters across %,d paragraphs", characters, elements);
        String message = String.format(
                "%s document '%s' timed out: %s. "
                + "The Google Docs API has no range read, so the whole document is transferred in a single "
                + "response and this only gets worse as the document grows. Split the notes document "
                + "(for example one per year) or archive its older sections.",
                action, doc.id(), size);
        log.error(message);
        return new RuntimeException(message, cause);
    }

    /**
     * Returns true if a HEADING_1 paragraph containing {@code meetingName} already has a date chip
     * whose title or URI contains the ISO-8601 date string for {@code date}.
     */
    static boolean agendaEntryExists(Document document, String meetingName, LocalDate date) {
        List<StructuralElement> content = document.getBody().getContent();
        if (content == null) return false;
        String isoDate = date.toString(); // "YYYY-MM-DD"
        for (StructuralElement element : content) {
            Paragraph paragraph = element.getParagraph();
            if (paragraph == null) continue;
            if (!isHeading(paragraph, "HEADING_1")) continue;
            if (!extractText(paragraph).contains(meetingName)) continue;
            if (paragraph.getElements() == null) continue;
            for (var pe : paragraph.getElements()) {
                var richLink = pe.getRichLink();
                if (richLink == null || richLink.getRichLinkProperties() == null) continue;
                String title = richLink.getRichLinkProperties().getTitle();
                String uri = richLink.getRichLinkProperties().getUri();
                log.debug("Checking date chip in '{}': title='{}', uri='{}', looking for '{}'",
                        meetingName, title, uri, isoDate);
                if ((title != null && title.contains(isoDate)) || (uri != null && uri.contains(isoDate))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<Request> buildHeadingStyleRequests(String text, int insertAt) {
        List<Request> requests = new ArrayList<>();
        int charPos = insertAt;
        for (String line : text.split("\n", -1)) {
            int lineStart = charPos;
            int lineEnd = charPos + line.length() + 1;

            String style = null;
            if (line.startsWith("> ") && !line.startsWith(">> ")) {
                style = "HEADING_2";
            } else if (line.startsWith(">> ")) {
                style = "HEADING_3";
            }

            if (style != null) {
                requests.add(new Request().setUpdateParagraphStyle(
                        new UpdateParagraphStyleRequest()
                                .setRange(new Range().setStartIndex(lineStart).setEndIndex(lineEnd))
                                .setParagraphStyle(new ParagraphStyle().setNamedStyleType(style))
                                .setFields("namedStyleType")
                ));
            }
            charPos = lineEnd;
        }
        return requests;
    }

    /**
     * Reads topic sections from the meeting's notes document.
     *
     * <p>Finds the last HEADING_1 paragraph whose text contains {@code meetingName}, then collects
     * each subsequent HEADING_2 block (a topic) with all following paragraph text until the next
     * HEADING_2 or HEADING_1. Only topics with non-empty body text are returned.</p>
     */
    @Override
    public List<TopicContent> readTopicNotes(DocRef doc, LocalDate date, String meetingName) {
        try {
            var docs = buildDocs();
            Document document;
            try {
                document = docs.documents().get(doc.id()).setFields(DOCUMENT_FIELDS).execute();
            } catch (SocketTimeoutException e) {
                throw oversizedDocument("Reading", doc, probeSize(docs, doc), e);
            }
            List<StructuralElement> content = document.getBody().getContent();
            if (content == null) return List.of();

            List<Paragraph> paragraphs = content.stream()
                    .map(StructuralElement::getParagraph)
                    .filter(p -> p != null)
                    .collect(Collectors.toList());

            // Find the last occurrence of a HEADING_1 containing meetingName
            int meetingHeadingIndex = -1;
            for (int i = 0; i < paragraphs.size(); i++) {
                if (isHeading(paragraphs.get(i), "HEADING_1")
                        && extractText(paragraphs.get(i)).contains(meetingName)) {
                    meetingHeadingIndex = i;
                }
            }

            if (meetingHeadingIndex < 0) {
                log.warn("Meeting heading '{}' not found in document {}", meetingName, doc.id());
                return List.of();
            }

            List<TopicContent> result = new ArrayList<>();
            String currentTopicName = null;
            TopicBody currentBody = new TopicBody();

            for (int i = meetingHeadingIndex + 1; i < paragraphs.size(); i++) {
                Paragraph p = paragraphs.get(i);
                String text = extractText(p).stripTrailing();

                if (isHeading(p, "HEADING_1")) break;  // next meeting section

                if (isTopicHeading(p)) {
                    if (currentTopicName != null) {
                        String body = currentBody.toString();
                        if (!body.isEmpty()) {
                            result.add(new TopicContent(currentTopicName, body));
                        }
                    }
                    currentTopicName = text.startsWith("> ") ? text.substring(2) : text;
                    currentBody = new TopicBody();
                } else if (currentTopicName != null && !text.isEmpty()) {
                    currentBody.append(p, text);
                }
            }

            if (currentTopicName != null) {
                String body = currentBody.toString();
                if (!body.isEmpty()) {
                    result.add(new TopicContent(currentTopicName, body));
                }
            }

            return result;

        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("Failed to read topic notes from document '" + doc.id() + "': " + e.getMessage(), e);
        }
    }

    private static boolean isHeading(Paragraph paragraph, String headingType) {
        ParagraphStyle style = paragraph.getParagraphStyle();
        return style != null && headingType.equals(style.getNamedStyleType());
    }

    private static boolean isTopicHeading(Paragraph paragraph) {
        return isHeading(paragraph, "HEADING_2") && extractText(paragraph).startsWith("> ");
    }

    private static String extractText(Paragraph paragraph) {
        if (paragraph.getElements() == null) return "";
        return paragraph.getElements().stream()
                .filter(e -> e.getTextRun() != null)
                .map(e -> e.getTextRun().getContent())
                .collect(Collectors.joining());
    }

    /**
     * Renders one notes paragraph as Trello-comment markdown.
     *
     * <p>Trello comments render bullet lists but not headings, so a heading paragraph
     * (e.g. a HEADING_3 section title inside the notes) is emitted as bold text instead —
     * otherwise it arrives as an unstyled line that Trello folds into the preceding bullet.</p>
     */
    static String formatBodyLine(Paragraph paragraph, String text) {
        String content = isHeadingStyle(paragraph) && !text.isBlank() ? "**" + text.strip() + "**" : text;
        Bullet bullet = paragraph.getBullet();
        if (bullet == null) return content;
        int level = bullet.getNestingLevel() != null ? bullet.getNestingLevel() : 0;
        return "  ".repeat(level) + "- " + content;
    }

    /** True for a heading paragraph that is not part of a bullet list. */
    static boolean isStandaloneHeading(Paragraph paragraph) {
        return paragraph.getBullet() == null && isHeadingStyle(paragraph);
    }

    private static boolean isHeadingStyle(Paragraph paragraph) {
        ParagraphStyle style = paragraph.getParagraphStyle();
        if (style == null) return false;
        String named = style.getNamedStyleType();
        return named != null
                && (named.startsWith("HEADING_") || named.equals("TITLE") || named.equals("SUBTITLE"));
    }

    /**
     * Accumulates the body text of one topic as Trello-comment markdown.
     *
     * <p>A standalone heading gets a blank line on both sides: without it Trello's markdown
     * reads the heading as a lazy continuation of the preceding bullet item and glues it onto
     * that line.</p>
     */
    static final class TopicBody {

        private final StringBuilder text = new StringBuilder();
        private boolean previousWasHeading;

        void append(Paragraph paragraph, String lineText) {
            boolean isHeading = isStandaloneHeading(paragraph);
            if (!text.isEmpty()) {
                text.append(previousWasHeading || isHeading ? "\n\n" : "\n");
            }
            text.append(formatBodyLine(paragraph, lineText));
            previousWasHeading = isHeading;
        }

        @Override
        public String toString() {
            return text.toString().stripTrailing();
        }
    }

    /** Navigates Drive folder hierarchy and returns the ID of the deepest folder, or null if not found. */
    private String navigateFolders(Drive drive, String[] folderParts) throws IOException {
        String parentId = "root";
        for (String part : folderParts) {
            var result = drive.files().list()
                    .setQ("name = '" + part.replace("'", "\\'") + "'"
                            + " and '" + parentId + "' in parents"
                            + " and mimeType = 'application/vnd.google-apps.folder'"
                            + " and trashed = false")
                    .setFields("files(id)")
                    .execute()
                    .getFiles();

            if (result == null || result.isEmpty()) return null;
            parentId = result.get(0).getId();
        }
        return parentId;
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private Drive buildDrive() throws IOException, GeneralSecurityException {
        return new Drive.Builder(auth.getTransport(), GsonFactory.getDefaultInstance(), auth.requestInitializer())
                .setApplicationName("PrepareMe")
                .build();
    }

    private Docs buildDocs() throws IOException, GeneralSecurityException {
        return new Docs.Builder(auth.getTransport(), GsonFactory.getDefaultInstance(), auth.requestInitializer())
                .setApplicationName("PrepareMe")
                .build();
    }
}
