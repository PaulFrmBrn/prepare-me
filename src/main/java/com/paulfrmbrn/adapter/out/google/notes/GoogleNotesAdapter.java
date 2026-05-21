package com.paulfrmbrn.adapter.out.google.notes;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.model.BatchUpdateDocumentRequest;
import com.google.api.services.docs.v1.model.DateElementProperties;
import com.google.api.services.docs.v1.model.Document;
import com.google.api.services.docs.v1.model.InsertDateRequest;
import com.google.api.services.docs.v1.model.InsertTextRequest;
import com.google.api.services.docs.v1.model.Location;
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
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class GoogleNotesAdapter implements MeetingNotesPort {

    private static final Logger log = LoggerFactory.getLogger(GoogleNotesAdapter.class);

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

            var document = docs.documents().get(doc.id()).execute();

            if (agendaEntryExists(document, meetingName, date)) {
                log.info("Agenda for '{}' on {} already exists in document {}, skipping", meetingName, date, doc.id());
                return;
            }

            int endIndex = document.getBody().getContent().stream()
                    .mapToInt(e -> e.getEndIndex() != null ? e.getEndIndex() : 0)
                    .max()
                    .orElse(1);
            int insertAt = Math.max(1, endIndex - 1);

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
            // 4. Insert date chip at insertAt+1 (before the space) — done last to avoid
            //    shifting the indices used in requests 2 and 3
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
                // Log all non-text elements to help diagnose how date chips are returned
                if (pe.getTextRun() == null) {
                    log.info("Non-text element in HEADING_1 '{}': richLink={}, person={}, inlineObject={}, equation={}, raw={}",
                            meetingName,
                            pe.getRichLink(),
                            pe.getPerson(),
                            pe.getInlineObjectElement(),
                            pe.getEquation(),
                            pe);
                    if (pe.getRichLink() != null) {
                        log.info("  RichLink props: title={}, uri={}, mimeType={}",
                                pe.getRichLink().getRichLinkProperties() != null ? pe.getRichLink().getRichLinkProperties().getTitle() : "null",
                                pe.getRichLink().getRichLinkProperties() != null ? pe.getRichLink().getRichLinkProperties().getUri() : "null",
                                pe.getRichLink().getRichLinkProperties() != null ? pe.getRichLink().getRichLinkProperties().getMimeType() : "null");
                    }
                }
                var richLink = pe.getRichLink();
                if (richLink == null || richLink.getRichLinkProperties() == null) continue;
                String title = richLink.getRichLinkProperties().getTitle();
                String uri = richLink.getRichLinkProperties().getUri();
                log.info("Checking date chip in '{}': title='{}', uri='{}', looking for '{}'",
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
            var document = docs.documents().get(doc.id()).execute();
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
            StringBuilder currentBody = new StringBuilder();

            for (int i = meetingHeadingIndex + 1; i < paragraphs.size(); i++) {
                Paragraph p = paragraphs.get(i);
                String text = extractText(p).stripTrailing();

                if (isHeading(p, "HEADING_1")) break;  // next meeting section

                if (isTopicHeading(p)) {
                    if (currentTopicName != null) {
                        String body = currentBody.toString().stripTrailing();
                        if (!body.isEmpty()) {
                            result.add(new TopicContent(currentTopicName, body));
                        }
                    }
                    currentTopicName = text.startsWith("> ") ? text.substring(2) : text;
                    currentBody = new StringBuilder();
                } else if (currentTopicName != null && !text.isEmpty()) {
                    if (currentBody.length() > 0) currentBody.append("\n");
                    currentBody.append(formatBodyLine(p, text));
                }
            }

            if (currentTopicName != null) {
                String body = currentBody.toString().stripTrailing();
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

    static String formatBodyLine(Paragraph paragraph, String text) {
        Bullet bullet = paragraph.getBullet();
        if (bullet == null) return text;
        int level = bullet.getNestingLevel() != null ? bullet.getNestingLevel() : 0;
        return "  ".repeat(level) + "- " + text;
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
        var transport = GoogleNetHttpTransport.newTrustedTransport();
        return new Drive.Builder(transport, GsonFactory.getDefaultInstance(), auth.getCredential())
                .setApplicationName("PrepareMe")
                .build();
    }

    private Docs buildDocs() throws IOException, GeneralSecurityException {
        var transport = GoogleNetHttpTransport.newTrustedTransport();
        return new Docs.Builder(transport, GsonFactory.getDefaultInstance(), auth.getCredential())
                .setApplicationName("PrepareMe")
                .build();
    }
}
