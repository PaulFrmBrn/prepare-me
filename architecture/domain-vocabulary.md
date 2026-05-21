# Domain Vocabulary

Key concepts used across architecture diagrams, code, and documentation.

---

## Core Concepts

**Meeting**
A calendar event that requires preparation. Not all calendar events are meetings — events listed in `~/.prepare-me/excluded-events.yaml` are excluded from Draft Plan processing. The exclusion list is configurable and can be edited via the Web UI. The Calendar Adapter itself only filters out all-day events and declined events. Fields: `name` (event title as shown in calendar), `attendees` (list of participant email addresses, populated by CalendarAdapter from Google Calendar data and used in `create-agenda` for MeetingType classification).

**MeetingType**
Classifies a meeting for the purpose of locating its notes document.
- `ONE_ON_ONE` — exactly 2 attendees (the user + one other person). Identified by the title pattern `<OtherName> / <UserName>` where the user's name may appear as *Dima*, *Dmitry*, or *Dmitrii*. The title may also include an optional prefix (e.g. a ticket id or chat label) and/or an optional suffix (e.g. a milestone description): `ODM-12259. Mikhail / Dmitry`, `Ivan / Dmitry - milestone 3`, `Random chat 2 - Gleb / Dmitry`.
- `GROUP` — 3 or more attendees, or any event that does not match the 1-1 title pattern.

**TopicCard**
An agenda item the user wants to cover in a meeting. Topic cards are created manually in the Planner (Add Topics step) and are positioned below the meeting's Planner card. The ordered list of topic cards under a meeting card forms its agenda. Each topic card carries a board-side `id` used to post comments (Save Notes phase).

**TopicContent**
A topic section read back from a notes document: `topicName` (matches the Trello topic card name) and `bodyText` (all content beneath the topic heading — checklist item lines and free-text notes the user wrote — excluding the heading line itself).

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

**Fill in Notes**
The manual step between Create Agenda and Save Notes: the user opens each meeting's notes document and writes text freely under each topic heading (decisions, action items, context). No special markup or placeholders are required. Not automated.

**Save Notes**
The automated step (`save-notes` command) that reads back each meeting's notes document, extracts all content beneath each topic heading (checklist item lines and any free text the user wrote — everything except the topic heading line itself), and posts it as a comment on the corresponding Trello topic card.

---

## Ports (Domain Boundaries)

**CalendarPort**
Provides filtered meetings for a given date. Hides the calendar provider (currently Google Calendar).
- `List<Meeting> getMeetings(LocalDate date)`

**MeetingBoardPort**
Creates meeting cards, reads the ordered card list with grouped topics, and posts comments on topic cards. Hides the planning tool (currently Trello).
- `void createCard(String name)` — used by Draft Plan
- `List<MeetingWithTopics> getMeetingsWithTopics()` — used by Create Agenda and Save Notes
- `void addTopicComment(String topicId, String comment)` — used by Save Notes

**MeetingNotesPort**
Locates a notes document by person/team name, appends a dated agenda, and reads back topic content. Hides the notes storage (currently Google Drive + Docs).
- `Optional<DocRef> findDoc(String drivePath)`
- `void appendAgenda(DocRef doc, LocalDate date, String meetingName, List<Topic> topics)`
- `List<TopicContent> readTopicNotes(DocRef doc, LocalDate date, String meetingName)` — used by Save Notes; returns one `TopicContent` per agenda topic that has non-empty body content

**ManualLinkResolverPort**
Resolves a notes document name for a meeting. Checks a separate local mapping file (`~/.prepare-me/doc-mappings.yaml`, path configurable via `docMappingsFile` in `settings.yaml`); if no entry is found, throws `MissingDocMappingException`. The calling use case catches this exception and either falls back to automatic path resolution (for 1-on-1 meetings) or skips the meeting with a warning (for group meetings). New mappings are added manually to the YAML file or through the Web UI settings. Hides the local storage mechanism (currently YAML file).
- `String resolveDocName(String meetingTitle)`

---

## Naming Conventions

| Pattern | Example | Meaning |
|---|---|---|
| Planner card name | `Meeting: Weekly Sync` | Created by `draft-plan`; marks a meeting in the Planner |
| 1-1 event title | `Ivan / Dima`, `ODM-12259. Mikhail / Dmitry`, `Ivan / Dmitry - milestone 3`, `Random chat 2 - Gleb / Dmitry` | User and other person separated by ` / `; either order; optional prefix/suffix allowed |
| Notes folder (1-1) | `_Notes/People` | Root-level Google Drive folder for 1-1 note documents |
| Notes folder (group) | `_Notes/Teams` | Root-level Google Drive folder for group/team note documents |
| Notes doc title | `Ivan` / `Иван` / `Ваня` | Named after the person or team; Latin or Cyrillic |
| Agenda heading | `2026-04-12` | Heading 1 inserted at end of notes doc, format `yyyy-MM-dd` |
