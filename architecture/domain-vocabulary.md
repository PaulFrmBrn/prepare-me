# Domain Vocabulary

Key concepts used across architecture diagrams, code, and documentation.

---

## Core Concepts

**Meeting**
A calendar event that requires preparation. Not all calendar events are meetings — events named *"NO MEETINGS, PLEASE"* and *"Lunch"* are excluded from all processing.

**MeetingType**
Classifies a meeting for the purpose of locating its notes document.
- `ONE_ON_ONE` — exactly 2 attendees (the user + one other person). Identified by the title pattern `<OtherName> / <UserName>` where the user's name may appear as *Dima*, *Dmitry*, or *Dmitrii*.
- `GROUP` — 3 or more attendees, or any event that does not match the 1-1 title pattern.

**TopicCard**
An agenda item the user wants to cover in a meeting. Topic cards are created manually in the Planner (OOTS) and are positioned below the meeting's Planner card. The ordered list of topic cards under a meeting card forms its agenda.

**Agenda**
The ordered list of TopicCards for a single meeting. Written into the meeting's notes document during Phase 2.

---

## Workflow Phases

**Phase 1 — Meeting Cards**
The automated step that reads the day's meetings from the Calendar and creates one Planner card per meeting, named `Meeting: <event title>`.

**Phase 2 — Meeting Notes**
The automated step that reads the ordered Planner cards (meetings + their topic cards), locates or receives the notes document for each meeting, and appends a dated heading with the agenda topics.

**OOTS (Out Of The Scope)**
Steps intentionally left to the user: reordering Planner cards and adding topic cards beneath each meeting card. These steps encode personal judgement and are not automated.

---

## Ports (Domain Boundaries)

**CalendarPort**
Provides filtered meetings for a given date. Hides the calendar provider (currently Google Calendar).

**MeetingBoardPort**
Creates meeting cards and reads the ordered card list with grouped topics. Hides the planning tool (currently Trello).

**MeetingNotesPort**
Locates a notes document by person/team name and appends a dated agenda. Hides the notes storage (currently Google Drive + Docs).

**ManualLinkResolverPort**
Asks the user to supply a document link when automatic lookup fails (group meetings). Hides the I/O mechanism (currently stdin/stdout).

---

## Naming Conventions

| Pattern | Example | Meaning |
|---|---|---|
| Planner card name | `Meeting: Weekly Sync` | Created by Phase 1; marks a meeting in the Planner |
| 1-1 event title | `Ivan / Dima` | User and other person separated by ` / `; either order |
| Notes folder (1-1) | `_Notes/People` | Root-level Google Drive folder for 1-1 note documents |
| Notes folder (group) | `_Notes/Teams` | Root-level Google Drive folder for group/team note documents |
| Notes doc title | `Ivan` / `Иван` / `Ваня` | Named after the person or team; Latin or Cyrillic |
| Agenda heading | `2026-04-12` | Heading 1 inserted at end of notes doc, format `yyyy-MM-dd` |
