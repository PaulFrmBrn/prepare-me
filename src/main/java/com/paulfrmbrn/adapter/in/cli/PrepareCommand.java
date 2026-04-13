package com.paulfrmbrn.adapter.in.cli;

import picocli.CommandLine.Command;

@Command(
        name = "prepare-me",
        mixinStandardHelpOptions = true,
        description = "Daily meeting preparation tool"
)
public class PrepareCommand {}
