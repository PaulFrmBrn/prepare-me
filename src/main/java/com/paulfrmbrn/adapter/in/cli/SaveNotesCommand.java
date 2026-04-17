package com.paulfrmbrn.adapter.in.cli;

import com.paulfrmbrn.domain.port.in.SaveMeetingNotesUseCase;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.LocalDate;
import java.util.concurrent.Callable;

@Command(
        name = "save-notes",
        mixinStandardHelpOptions = true,
        description = "Read topic notes from notes documents and post them as comments on Trello topic cards"
)
public class SaveNotesCommand implements Callable<Integer> {

    @Option(names = "--date", description = "Date in yyyy-MM-dd format (default: today)")
    private final LocalDate date = LocalDate.now();

    private final SaveMeetingNotesUseCase useCase;

    public SaveNotesCommand(SaveMeetingNotesUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public Integer call() {
        var results = useCase.execute(date);
        if (results.isEmpty()) {
            System.out.println("No meetings found for " + date + ".");
        } else {
            int total = results.values().stream().mapToInt(Integer::intValue).sum();
            System.out.println("Notes saved for " + results.size() + " meeting(s) on " + date
                    + " (" + total + " comment(s) posted):");
            int i = 1;
            for (var entry : results.entrySet()) {
                System.out.println("  " + i++ + ". " + entry.getKey()
                        + ": " + entry.getValue() + " topic(s)");
            }
        }
        return 0;
    }
}
