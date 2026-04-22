package com.paulfrmbrn;

import com.paulfrmbrn.adapter.in.cli.CreateAgendaCommand;
import com.paulfrmbrn.adapter.in.cli.DraftPlanCommand;
import com.paulfrmbrn.adapter.in.cli.PrepareCommand;
import com.paulfrmbrn.adapter.in.cli.SaveNotesCommand;
import com.paulfrmbrn.adapter.in.cli.WebCommand;
import com.paulfrmbrn.adapter.out.google.auth.GoogleAuthProvider;
import com.paulfrmbrn.adapter.out.google.calendar.GoogleCalendarAdapter;
import com.paulfrmbrn.adapter.out.google.notes.GoogleNotesAdapter;
import com.paulfrmbrn.adapter.out.mapping.ManualDocNameResolverAdapter;
import com.paulfrmbrn.adapter.out.trello.TrelloAdapter;
import com.paulfrmbrn.domain.usecase.CreateMeetingCards;
import com.paulfrmbrn.domain.usecase.PrepareMeetingNotes;
import com.paulfrmbrn.domain.usecase.SaveMeetingNotes;
import com.paulfrmbrn.infrastructure.Settings;
import picocli.CommandLine;

import java.nio.file.Path;

public class Main {

    public static void main(String[] args) {
        try {
            Path configPath = Settings.defaultPath();
            Settings s = Settings.load(configPath);

            var googleAuth = new GoogleAuthProvider(
                    Settings.expand(s.google.credentialsFile),
                    Settings.expand(s.google.tokensDir));

            var calendarAdapter = new GoogleCalendarAdapter(googleAuth);
            var plannerAdapter  = new TrelloAdapter(s.trello.apiKey, s.trello.apiToken,
                                                    s.trello.boardName, s.trello.meetingsListName);
            var notesAdapter    = new GoogleNotesAdapter(googleAuth);

            Path docMappingsPath = Settings.resolveDocMappingsPath(s.docMappingsFile);
            var resolverAdapter = new ManualDocNameResolverAdapter(docMappingsPath);

            Path excludedEventsPath = Settings.resolveExcludedEventsPath(s.excludedEventsFile);
            var excludedEvents = new java.util.HashSet<>(Settings.loadExcludedEvents(excludedEventsPath));

            var createMeetingCards  = new CreateMeetingCards(calendarAdapter, plannerAdapter, excludedEvents);
            var prepareMeetingNotes = new PrepareMeetingNotes(plannerAdapter, calendarAdapter,
                                                              notesAdapter, resolverAdapter, s.notesDir);
            var saveMeetingNotes    = new SaveMeetingNotes(plannerAdapter, calendarAdapter,
                                                           notesAdapter, resolverAdapter, s.notesDir);

            int exit = new CommandLine(new PrepareCommand())
                    .addSubcommand("draft-plan",    new DraftPlanCommand(createMeetingCards))
                    .addSubcommand("create-agenda", new CreateAgendaCommand(prepareMeetingNotes))
                    .addSubcommand("save-notes",    new SaveNotesCommand(saveMeetingNotes))
                    .addSubcommand("serve",         new WebCommand(s))
                    .setExecutionExceptionHandler((ex, cmd, _) -> {
                        cmd.getErr().println("Error: " + ex.getMessage());
                        return 1;
                    })
                    .execute(args);

            System.exit(exit);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
