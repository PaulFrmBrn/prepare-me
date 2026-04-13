package com.paulfrmbrn;

import com.paulfrmbrn.adapter.in.cli.DraftPlanCommand;
import com.paulfrmbrn.adapter.in.cli.PrepareCommand;
import com.paulfrmbrn.adapter.out.google.auth.GoogleAuthProvider;
import com.paulfrmbrn.adapter.out.google.calendar.GoogleCalendarAdapter;
import com.paulfrmbrn.adapter.out.trello.TrelloAdapter;
import com.paulfrmbrn.domain.usecase.CreateMeetingCards;
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
            var createMeetingCards = new CreateMeetingCards(calendarAdapter, plannerAdapter);

            int exit = new CommandLine(new PrepareCommand())
                    .addSubcommand("draft-plan", new DraftPlanCommand(createMeetingCards))
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
