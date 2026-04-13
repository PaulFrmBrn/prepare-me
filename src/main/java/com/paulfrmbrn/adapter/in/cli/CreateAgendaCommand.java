package com.paulfrmbrn.adapter.in.cli;

import com.paulfrmbrn.domain.port.in.PrepareMeetingNotesUseCase;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
        name = "create-agenda",
        mixinStandardHelpOptions = true,
        description = "Read Trello meeting cards with topics and append agendas to notes documents"
)
public class CreateAgendaCommand implements Callable<Integer> {

    @Option(names = "--date", description = "Date in yyyy-MM-dd format (default: today)")
    private final LocalDate date = LocalDate.now();

    private final PrepareMeetingNotesUseCase useCase;

    public CreateAgendaCommand(PrepareMeetingNotesUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public Integer call() {
        var meetings = useCase.execute(date);
        if (meetings.isEmpty()) {
            System.out.println("No meetings found for " + date + ".");
        } else {
            System.out.println("Agenda prepared for " + meetings.size() + " meeting(s) on " + date + ":");
            int i = 1;
            for (var entry : meetings.entrySet()) {
                System.out.println("  " + i++ + ". " + entry.getKey() + ": " + entry.getValue());
            }
        }
        return 0;
    }
}
