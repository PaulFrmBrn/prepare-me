package com.paulfrmbrn.adapter.in.cli;

import com.paulfrmbrn.domain.port.in.CreateMeetingCardsUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Phase1CommandTest {

    @Mock CreateMeetingCardsUseCase useCase;

    ByteArrayOutputStream out;
    PrintStream originalOut;

    @BeforeEach
    void captureStdout() {
        originalOut = System.out;
        out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
    }

    @Test
    void printsEachCreatedCard() {
        when(useCase.execute(any())).thenReturn(List.of("Meeting: Team Sync", "Meeting: 1:1 with Ivan"));

        run("--date", "2026-04-14");

        assertThat(out.toString())
                .contains("Created 2 card(s)")
                .contains("Meeting: Team Sync")
                .contains("Meeting: 1:1 with Ivan");
    }

    @Test
    void printsNoMeetingsMessageWhenListIsEmpty() {
        when(useCase.execute(any())).thenReturn(List.of());

        run("--date", "2026-04-14");

        assertThat(out.toString()).contains("No meetings found");
    }

    @Test
    void passesDateToUseCase() {
        when(useCase.execute(LocalDate.of(2026, 4, 14))).thenReturn(List.of("Meeting: Sync"));

        run("--date", "2026-04-14");

        assertThat(out.toString()).contains("Meeting: Sync");
    }

    private void run(String... args) {
        new CommandLine(new Phase1Command(useCase)).execute(args);
    }
}
