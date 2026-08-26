# Prepare Me

A CLI tool that automates the daily meeting preparation routine — turning a Google Calendar day into a Trello board ready for stand-up and a set of Google Docs pre-loaded with meeting agendas.

## What it does

Each working day involves two automated phases separated by a short manual step:

**Draft Plan** — Pull today's meetings from Google Calendar and create a Trello card for each one in the *Meetings* list of the *work* board.

**Add Topics (not automated)** — Reorder the Trello cards and add topic cards beneath each meeting card following your own judgement.

**Create Agenda** — Read the ordered Trello cards, locate the Google Doc for each meeting, and append a dated heading (`yyyy-MM-dd`) with the agenda topics pulled from Trello.

At the end of Create Agenda the tool prints a numbered list of Google Doc links — one per meeting — ready to open before each call.

## Setup

### 1. Google credentials

The app needs an OAuth client to read your Google Calendar and Google Drive.

1. Go to [console.cloud.google.com](https://console.cloud.google.com) and sign in with your Google account. **Dismiss** the free trial banner — you don't need it and won't be charged.
2. Click **"Select a project"** (top-left) → **"New Project"** → name it `PrepareMe` → **Create**.
3. In the top search bar search for **"Google Calendar API"** → open the result → **Enable**. Repeat for **"Google Drive API"**.
4. Left menu (☰) → **APIs & Services** → **OAuth consent screen** → choose **External** → fill in:
   - App name: `PrepareMe`
   - User support email: your email
   - Developer contact email: your email
   
   Click **Save and Continue** through the next two screens (no changes needed). On the **Test users** screen add your own email address, then click **Save and Continue**.
5. Left menu → **APIs & Services** → **Credentials** → **+ Create Credentials** → **OAuth client ID** → Application type: **Desktop app** → name it `PrepareMe` → **Create**.
6. In the confirmation dialog click **Download JSON**. Save the file as:
   ```
   ~/.prepare-me/google-credentials.json
   ```

On the first run the app will open a browser tab asking you to approve access. After you approve, the token is saved to `~/.prepare-me/tokens/` and reused on every subsequent run.

### 2. Trello credentials

1. Go to [trello.io/app-key](https://trello.io/app-key) and sign in with your Trello account.
2. Click **New** (top-right) → fill in:
   - Name: `PrepareMe`
   - Workspace: pick any of your workspaces
   - Click **Create**
3. Your **API key** is shown on the next page — copy it.
4. On that same page click the **Token** link (shown just below the API key) → click **Allow** → copy the long token string shown.

### 3. Configuration file

Copy the template and fill in your credentials:

```bash
cp settings.yaml ~/.prepare-me/settings.yaml
```

Open `~/.prepare-me/settings.yaml` and set:
- `trello.apiKey` — the key from step 3
- `trello.apiToken` — the token from step 4
- `trello.boardName` — exact name of your Trello board (case-sensitive)
- `trello.meetingsListName` — exact name of the list to create cards in
- `docMappingsFile` — path to `doc-mappings.yaml`, which maps meetings to notes documents in Google Drive for `create-agenda` and `save-notes`. It has two sections: `titles` (exact calendar event title → document) and `prefixes` (title prefix → document, e.g. `"AIT:"` maps every `AIT: ...` team meeting to one document; the longest matching prefix wins and an exact `titles` entry always wins over a prefix). 1-1 meetings named `Name / Dmitry` need no entry — they resolve to `People/<Name>` automatically. A group meeting matching neither section is skipped with a message telling you what to add.
- `excludedEvents` — list of calendar event titles to ignore during `draft-plan` (e.g. "Out of office", "Lunch"). The template includes common defaults; extend it for any recurring non-meeting events on your calendar.

The Google credentials path is already set to the correct default.

Alternatively, you can skip copying and edit `settings.yaml` directly in the project root — the app checks the project root first before falling back to `~/.prepare-me/settings.yaml`.

### 4. Build

```bash
gradle wrapper        # only needed once if ./gradlew doesn't exist yet
./gradlew installDist # builds the app to build/install/prepare-me/
```

## Web UI

The app ships with a browser-based UI for running phases and editing settings on the fly.

### Start

```bash
./gradlew run --args="serve"               # starts on http://localhost:8080
./gradlew run --args="serve --port 9090"   # custom port
```

Or build and start in one step:

```bash
./gradlew installDist && build/install/prepare-me/bin/prepare-me serve
```

Then open **http://localhost:8080** in your browser.

### What you can do in the UI

- **Run Phase** — pick a date and click *Draft Plan*, *Create Agenda*, or *Save Notes*. Results appear inline.
- **Excluded Events** — edit the list of calendar event titles that are skipped during *Draft Plan*. Click **Save Excluded Events** and the next phase run will see the updated list immediately (no restart needed).
- **Doc Mappings** — edit the `Meeting Title = Drive Path` entries used by *Create Agenda* for group meetings. A trailing `*` on the key makes it a title-prefix rule (`AIT:* = _Notes/Teams/AIT`). Click **Save Doc Mappings** and the next phase run will see the changes immediately.

> Settings are stored in `~/.prepare-me/excluded-events.yaml` and `~/.prepare-me/doc-mappings.yaml` — the same files used by the CLI commands.

## Usage

### Phase 1 — Draft Plan

Pulls today's meetings from Google Calendar and creates a Trello card for each one in the Meetings list.

```bash
./gradlew run --args="draft-plan"           # today
./gradlew run --args="draft-plan --date 2026-04-14"   # specific date
```

The Meetings list must be empty before running — clear it manually if it still has cards from the previous day.

### Phase 1.5 — Add Topics (manual)

Open Trello. Below each meeting card, add topic cards — one per topic you want to discuss. Reorder cards as needed. Each topic card can have Trello checklists; `create-agenda` will pick up the first unchecked item per checklist.

### Phase 2 — Create Agenda

Reads the Meetings list from Trello, finds the Google Doc for each meeting, and appends a dated agenda section with the topics and checklist items.

```bash
./gradlew run --args="create-agenda"           # today
./gradlew run --args="create-agenda --date 2026-04-14"   # specific date
```

The command prints a numbered list of Google Doc links — one per processed meeting — ready to open before each call.

Group meetings matched by neither the `titles` nor the `prefixes` section of `~/.prepare-me/doc-mappings.yaml` are skipped with a message showing the exact entry to add.

---

> **Tip:** use the installed binary instead of `./gradlew run` once you have a stable build:
> ```bash
> ./gradlew installDist
> build/install/prepare-me/bin/prepare-me draft-plan
> build/install/prepare-me/bin/prepare-me create-agenda
> ```
> Re-run `./gradlew installDist` after any code change.

On the first run a browser tab will open for Google OAuth — approve it and the token is saved to `~/.prepare-me/tokens/` and reused on every subsequent run.

## Architecture

| Artifact | Description |
|---|---|
| [C1 — System Context](architecture/c1-system-context.puml) | The app and its external dependencies |
| [C2 — Containers](architecture/c2-containers.puml) | Internal structure and adapter boundaries |
| [Sequence — Draft Plan](architecture/sequence-draft-plan.md) | End-to-end flow for the draft-plan command |
| [Sequence — Create Agenda](architecture/sequence-create-agenda.md) | End-to-end flow for the create-agenda command |
| [Domain Vocabulary](architecture/domain-vocabulary.md) | Key concepts, port definitions, and naming conventions |

The application follows a hexagonal (ports & adapters) architecture. The domain layer has no dependency on Google, Trello, or any I/O framework. See [Domain Vocabulary](architecture/domain-vocabulary.md) for the full concept map.
