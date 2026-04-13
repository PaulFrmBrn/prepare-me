# Domain Vocabulary

Key concepts used across architecture diagrams, code, and documentation.

---

## Core Concepts

**Meeting**
A calendar event that requires preparation. Not all calendar events are meetings — events named *"NO MEETINGS, PLEASE"* and *"Lunch"* are excluded from all processing. Fields: `name` (event title as shown in calendar), `attendees` (list of participant email addresses, populated by CalendarAdapter from Google Calendar data and used in `create-agenda` for MeetingType classification).

**MeetingType**
Classifies a meeting for the purpose of locating its notes document.
- `ONE_ON_ONE` — exactly 2 attendees (the user + one other person). Identified by the title pattern `<OtherName> / <UserName>` where the user's name may appear as *Dima*, *Dmitry*, or *Dmitrii*.
- `GROUP` — 3 or more attendees, or any event that does not match the 1-1 title pattern.

**TopicCard**
An agenda item the user wants to cover in a meeting. Topic cards are created manually in the Planner (Add Topics step) and are positioned below the meeting's Planner card. The ordered list of topic cards under a meeting card forms its agenda.

**Agenda**
The ordered list of TopicCards for a single meeting. Written into the meeting's notes document during Create Agenda.

---

## Workflow Phases

**Draft Plan**
The automated step (`draft-plan` command) that reads the day's meetings from the Calendar and creates one Planner card per meeting, named `Meeting: <event title>`.

**Add Topics**
The manual step between Draft Plan and Create Agenda: the user reorders Planner cards and adds topic cards beneath each meeting card. These steps encode personal judgement and are not automated.

**Create Agenda**
The automated step (`create-agenda` command) that reads the ordered Planner cards (meetings + their topic cards), locates or receives the notes document for each meeting, and appends a dated heading with the agenda topics.

---

## Ports (Domain Boundaries)

**CalendarPort**
Provides filtered meetings for a given date. Hides the calendar provider (currently Google Calendar).
- `List<Meeting> getMeetings(LocalDate date)`

**MeetingBoardPort**
Creates meeting cards and reads the ordered card list with grouped topics. Hides the planning tool (currently Trello).
- `void createCard(String name)` — used by Draft Plan
- `List<MeetingWithTopics> getMeetingsWithTopics()` — used by Create Agenda

**MeetingNotesPort**
Locates a notes document by person/team name and appends a dated agenda. Hides the notes storage (currently Google Drive + Docs).
- `Optional<DocRef> findDoc(String name, MeetingType type)`
- `void appendAgenda(DocRef doc, LocalDate date, List<String> topics)`

**ManualLinkResolverPort**
Resolves a notes document name for a meeting when automatic lookup fails. First checks a local mapping file (`~/.prepare-me/doc-mappings.yaml`); if no entry is found, prompts the user to type the document name (not a URL) and persists the new mapping for future runs. Hides the I/O mechanism and local storage (currently stdin/stdout + YAML file).
- `String resolveDocName(String meetingTitle)`

---

## Naming Conventions

| Pattern | Example | Meaning |
|---|---|---|
| Planner card name | `Meeting: Weekly Sync` | Created by `draft-plan`; marks a meeting in the Planner |
| 1-1 event title | `Ivan / Dima` | User and other person separated by ` / `; either order |
| Notes folder (1-1) | `_Notes/People` | Root-level Google Drive folder for 1-1 note documents |
| Notes folder (group) | `_Notes/Teams` | Root-level Google Drive folder for group/team note documents |
| Notes doc title | `Ivan` / `Иван` / `Ваня` | Named after the person or team; Latin or Cyrillic |
| Agenda heading | `2026-04-12` | Heading 1 inserted at end of notes doc, format `yyyy-MM-dd` |
