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

Java 23 Gradle (Kotlin DSL) project (`group: com.paulfrmbrn`, `name: prepare-me`). Currently a minimal starter — single entry point at `src/main/java/com/paulfrmbrn/Main.java`.

JUnit 5 is configured as the test framework. Add test classes under `src/test/java/com/paulfrmbrn/`.