package com.paulfrmbrn.adapter.in.cli;

import com.paulfrmbrn.domain.port.in.CreateMeetingCardsUseCase;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.LocalDate;
import java.util.concurrent.Callable;

@Command(
        name = "draft-plan",
        mixinStandardHelpOptions = true,
        description = "Fetch today's calendar meetings and create Trello cards for each one"
)
public class DraftPlanCommand implements Callable<Integer> {

    @Option(names = "--date", description = "Date in yyyy-MM-dd format (default: today)")
    private final LocalDate date = LocalDate.now();

    private final CreateMeetingCardsUseCase useCase;

    public DraftPlanCommand(CreateMeetingCardsUseCase useCase) {
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
