# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
./gradlew compileJava   # Compile source code
./gradlew test          # Run tests
./gradlew build         # Compile, test, and build JAR to build/libs/
./gradlew clean build   # Clean and rebuild
./gradlew installDist   # Build a runnable install to build/install/prepare-me/
```

Every action is a Picocli subcommand, so `run` needs `--args` — bare `./gradlew run` only prints help:

```bash
./gradlew run --args="draft-plan"                      # calendar -> Trello cards (today)
./gradlew run --args="create-agenda --date 2026-04-14" # Trello topics -> notes docs
./gradlew run --args="save-notes"                      # notes docs -> Trello comments
./gradlew run --args="serve --port 9090"               # web UI, default port 8080
```

All three phase commands take `--date yyyy-MM-dd` (default: today). For repeated runs prefer the
installed binary, which skips Gradle startup — `build/install/prepare-me/bin/prepare-me draft-plan`
— and re-run `./gradlew installDist` after each code change.

## Project Overview

Java 21 Gradle (Kotlin DSL) project (`group: com.paulfrmbrn`, `name: prepare-me`). Entry point: `com.paulfrmbrn.Main` (composition root — wires adapters into use cases and registers Picocli subcommands).

Hexagonal architecture: domain ports in `domain/port/`, use cases in `domain/usecase/`, adapters in `adapter/out/` (Google Calendar, Trello) and `adapter/in/cli/` (plus `adapter/in/web/` for the `serve` UI).

Configuration lives in three YAML files — `settings.yaml`, `excluded-events.yaml`, `doc-mappings.yaml`. Each is read from the project root first and from `~/.prepare-me/` otherwise (see `infrastructure/Settings`), so a copy left in the working directory silently shadows the user's real config. The project root holds template copies of all three. First run opens a browser for Google OAuth; tokens are cached in `~/.prepare-me/tokens/`.

Tests use JUnit 5 + Mockito + AssertJ. Unit tests live alongside the domain layer in `src/test/java/com/paulfrmbrn/domain/`.