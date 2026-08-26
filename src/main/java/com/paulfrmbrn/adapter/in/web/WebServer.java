package com.paulfrmbrn.adapter.in.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfrmbrn.adapter.out.google.auth.GoogleAuthProvider;
import com.paulfrmbrn.adapter.out.google.calendar.GoogleCalendarAdapter;
import com.paulfrmbrn.adapter.out.google.notes.GoogleNotesAdapter;
import com.paulfrmbrn.adapter.out.mapping.ManualDocNameResolverAdapter;
import com.paulfrmbrn.adapter.out.trello.TrelloAdapter;
import com.paulfrmbrn.domain.usecase.CreateMeetingCards;
import com.paulfrmbrn.domain.usecase.PrepareMeetingNotes;
import com.paulfrmbrn.domain.usecase.SaveMeetingNotes;
import com.paulfrmbrn.infrastructure.Settings;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class WebServer {

    private static final Logger log = LoggerFactory.getLogger(WebServer.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Settings settings;
    private final Path excludedEventsPath;
    private final Path docMappingsPath;
    private final GoogleAuthProvider googleAuth;
    private final TrelloAdapter trelloAdapter;
    private final GoogleCalendarAdapter calendarAdapter;
    private final GoogleNotesAdapter notesAdapter;

    public WebServer(Settings settings) {
        this.settings = settings;
        this.excludedEventsPath = Settings.resolveExcludedEventsPath(settings.excludedEventsFile);
        this.docMappingsPath = Settings.resolveDocMappingsPath(settings.docMappingsFile);
        this.googleAuth = new GoogleAuthProvider(
                Settings.expand(settings.google.credentialsFile),
                Settings.expand(settings.google.tokensDir));
        this.trelloAdapter = new TrelloAdapter(
                settings.trello.apiKey, settings.trello.apiToken,
                settings.trello.boardName, settings.trello.meetingsListName);
        this.calendarAdapter = new GoogleCalendarAdapter(googleAuth);
        this.notesAdapter = new GoogleNotesAdapter(googleAuth);
    }

    public void start(int port) {
        log.info("excluded-events path : {}", excludedEventsPath.toAbsolutePath());
        log.info("doc-mappings path    : {}", docMappingsPath.toAbsolutePath());

        var app = Javalin.create(config ->
                config.staticFiles.add("/static", Location.CLASSPATH)
        ).start(port);

        app.exception(Exception.class, (e, ctx) -> {
            log.error("Request failed", e);
            ctx.status(500).json(Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        });

        app.get("/", ctx -> ctx.redirect("/index.html"));

        // ── Settings ──────────────────────────────────────────────────────

        app.get("/api/settings/excluded-events", ctx ->
                ctx.json(Settings.loadExcludedEvents(excludedEventsPath)));

        app.put("/api/settings/excluded-events", ctx -> {
            List<String> events = JSON.readValue(ctx.body(), new TypeReference<List<String>>() {});
            Settings.saveExcludedEvents(excludedEventsPath, events);
            ctx.status(204);
        });

        app.get("/api/settings/doc-mappings", ctx ->
                ctx.json(Settings.loadDocMappings(docMappingsPath)));

        app.put("/api/settings/doc-mappings", ctx -> {
            var mappings = JSON.readValue(ctx.body(), Settings.DocMappings.class);
            Settings.saveDocMappings(docMappingsPath, mappings);
            ctx.status(204);
        });

        // ── Phases ────────────────────────────────────────────────────────

        app.post("/api/phases/draft-plan", ctx -> {
            LocalDate date = parseDate(ctx.queryParam("date"));
            // Re-read excluded events so UI edits are picked up immediately
            var excluded = new HashSet<>(Settings.loadExcludedEvents(excludedEventsPath));
            var useCase = new CreateMeetingCards(calendarAdapter, trelloAdapter, excluded);
            ctx.json(Map.of("cards", useCase.execute(date)));
        });

        app.post("/api/phases/create-agenda", ctx -> {
            LocalDate date = parseDate(ctx.queryParam("date"));
            // Create fresh resolver so doc-mapping edits are picked up immediately
            var resolver = new ManualDocNameResolverAdapter(docMappingsPath);
            var useCase = new PrepareMeetingNotes(trelloAdapter, calendarAdapter, notesAdapter, resolver, settings.notesDir);
            ctx.json(useCase.execute(date));
        });

        app.post("/api/phases/save-notes", ctx -> {
            LocalDate date = parseDate(ctx.queryParam("date"));
            var resolver = new ManualDocNameResolverAdapter(docMappingsPath);
            var useCase = new SaveMeetingNotes(trelloAdapter, calendarAdapter, notesAdapter, resolver, settings.notesDir);
            ctx.json(useCase.execute(date));
        });

        log.info("Web UI available at http://localhost:{}", port);
    }

    private LocalDate parseDate(String dateStr) {
        return dateStr != null && !dateStr.isBlank() ? LocalDate.parse(dateStr) : LocalDate.now();
    }
}
