ok# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
./gradlew compileJava   # Compile source code
./gradlew test          # Run tests
./gradlew build         # Compile, test, and build JAR to build/libs/
./gradlew clean build   # Clean and rebuild
./gradlew run           # Run the application
```

## Project Overview

Java 23 Gradle (Kotlin DSL) project (`group: com.paulfrmbrn`, `name: prepare-me`). Entry point: `com.paulfrmbrn.Main` (composition root — wires adapters into use cases and registers Picocli subcommands).

Hexagonal architecture: domain ports in `domain/port/`, use cases in `domain/usecase/`, adapters in `adapter/out/` (Google Calendar, Trello) and `adapter/in/cli/`. Settings loaded from `~/.prepare-me/settings.yaml` — copy `settings.yaml` from project root as a template.

Tests use JUnit 5 + Mockito + AssertJ. Unit tests live alongside the domain layer in `src/test/java/com/paulfrmbrn/domain/`.