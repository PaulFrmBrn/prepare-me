package com.paulfrmbrn.adapter.in.cli;

import com.paulfrmbrn.domain.port.in.CreateMeetingCardsUseCase;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.LocalDate;
import java.util.concurrent.Callable;

@Command(
        name = "phase1",
        mixinStandardHelpOptions = true,
        description = "Fetch today's calendar meetings and create Trello cards for each one"
)
public class Phase1Command implements Callable<Integer> {

    @Option(names = "--date", description = "Date in yyyy-MM-dd format (default: today)")
    private LocalDate date = LocalDate.now();

    private final CreateMeetingCardsUseCase useCase;

    public Phase1Command(CreateMeetingCardsUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public Integer call() {
        var cards = useCase.execute(date);
        if (cards.isEmpty()) {
            System.out.println("No meetings found for " + date + ".");
        } else {
            System.out.println("Created " + cards.size() + " card(s) for " + date + ":");
            cards.forEach(c -> System.out.println("  " + c));
        }
        return 0;
    }
}
