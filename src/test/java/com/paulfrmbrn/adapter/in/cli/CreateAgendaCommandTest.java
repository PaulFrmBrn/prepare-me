package com.paulfrmbrn.adapter.in.cli;

import com.paulfrmbrn.domain.port.in.PrepareMeetingNotesUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateAgendaCommandTest {

    @Mock PrepareMeetingNotesUseCase useCase;

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
    void printsNumberedListOfDocUrls() {
        var meetings = new LinkedHashMap<String, String>();
        meetings.put("Weekly Sync", "https://docs.google.com/doc1");
        meetings.put("Ivan / Dima", "https://docs.google.com/doc2");
        when(useCase.execute(any())).thenReturn(meetings);

        run();

        assertThat(out.toString())
                .contains("Agenda prepared for 2 meeting(s)")
                .contains("1. Weekly Sync: https://docs.google.com/doc1")
                .contains("2. Ivan / Dima: https://docs.google.com/doc2");
    }

    @Test
    void printsNoMeetingsMessageWhenListIsEmpty() {
        when(useCase.execute(any())).thenReturn(Map.of());

        run();

        assertThat(out.toString()).contains("No meetings found");
    }

    private void run() {
        new CommandLine(new CreateAgendaCommand(useCase)).execute("--date", "2026-04-14");
    }
}
