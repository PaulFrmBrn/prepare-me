package com.paulfrmbrn.adapter.out.google.notes;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.model.BatchUpdateDocumentRequest;
import com.google.api.services.docs.v1.model.InsertTextRequest;
import com.google.api.services.docs.v1.model.Location;
import com.google.api.services.docs.v1.model.ParagraphStyle;
import com.google.api.services.docs.v1.model.Range;
import com.google.api.services.docs.v1.model.Request;
import com.google.api.services.docs.v1.model.UpdateParagraphStyleRequest;
import com.google.api.services.drive.Drive;
import com.paulfrmbrn.adapter.out.google.auth.GoogleAuthProvider;
import com.paulfrmbrn.domain.model.DocRef;
import com.paulfrmbrn.domain.port.out.MeetingNotesPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    public void appendAgenda(DocRef doc, LocalDate date, List<String> topics) {
        try {
            var docs = buildDocs();

            var document = docs.documents().get(doc.id()).execute();
            int endIndex = document.getBody().getContent().stream()
                    .mapToInt(e -> e.getEndIndex() != null ? e.getEndIndex() : 0)
                    .max()
                    .orElse(1);
            int insertAt = Math.max(1, endIndex - 1);

            StringBuilder sb = new StringBuilder();
            sb.append("\n").append(date.toString()).append("\n");
            for (String topic : topics) {
                sb.append(topic).append("\n");
            }

            List<Request> requests = new ArrayList<>();
            requests.add(new Request().setInsertText(
                    new InsertTextRequest()
                            .setText(sb.toString())
                            .setLocation(new Location().setIndex(insertAt))
            ));

            int headingStart = insertAt + 1;
            int headingEnd = headingStart + date.toString().length() + 1;
            requests.add(new Request().setUpdateParagraphStyle(
                    new UpdateParagraphStyleRequest()
                            .setRange(new Range().setStartIndex(headingStart).setEndIndex(headingEnd))
                            .setParagraphStyle(new ParagraphStyle().setNamedStyleType("HEADING_1"))
                            .setFields("namedStyleType")
            ));

            docs.documents().batchUpdate(doc.id(),
                    new BatchUpdateDocumentRequest().setRequests(requests)).execute();

            log.debug("Appended agenda for {} to document {}", date, doc.id());

        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("Failed to append agenda to document '" + doc.id() + "': " + e.getMessage(), e);
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
