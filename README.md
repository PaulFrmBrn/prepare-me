# Prepare Me

A CLI tool that automates the daily meeting preparation routine — turning a Google Calendar day into a Trello board ready for stand-up and a set of Google Docs pre-loaded with meeting agendas.

## What it does

Each working day involves two automated phases separated by a short manual step:

**Phase 1** — Pull today's meetings from Google Calendar and create a Trello card for each one in the *Meetings* list of the *work* board.

**Manual step (not automated)** — Reorder the Trello cards and add topic cards beneath each meeting card following your own judgement.

**Phase 2** — Read the ordered Trello cards, locate the Google Doc for each meeting, and append a dated heading (`yyyy-MM-dd`) with the agenda topics pulled from Trello.

At the end of Phase 2 the tool prints a numbered list of Google Doc links — one per meeting — ready to open before each call.

## Usage

```bash
# Create meeting cards for today
./gradlew run --args="phase1 --date 2026-04-12"

# After adding topic cards manually in Trello:
./gradlew run --args="phase2 --date 2026-04-12"
```

First run will open a browser for Google OAuth and prompt for a Trello PIN. Tokens are stored in `~/.prepare-me/tokens/`.

## Architecture

| Artifact | Description |
|---|---|
| [C1 — System Context](architecture/c1-system-context.puml) | The app and its external dependencies |
| [C2 — Containers](architecture/c2-containers.puml) | Internal structure and adapter boundaries |
| [Sequence — Basic Flow](architecture/sequence-basic-flow.md) | End-to-end flow across both phases |
| [Domain Vocabulary](architecture/domain-vocabulary.md) | Key concepts, port definitions, and naming conventions |

The application follows a hexagonal (ports & adapters) architecture. The domain layer has no dependency on Google, Trello, or any I/O framework. See [Domain Vocabulary](architecture/domain-vocabulary.md) for the full concept map.
