# Sequence Diagram — Create Agenda (phase 2)

```mermaid
sequenceDiagram
    actor User
    participant CLI as Prepare Me CLI
    participant UC as Use Cases
    participant Cal as Calendar Adapter
    participant GCal as Google Calendar API
    participant Planner as Planner Adapter
    participant TrelloAPI as Trello REST API
    participant Notes as Notes Storage Adapter
    participant DriveAPI as Google Drive API
    participant DocsAPI as Google Docs API
    participant Mapping as Doc Name Mapping<br/>(~/.prepare-me/doc-mappings.yaml)

    User->>CLI: prepare-me create-agenda --date 2026-04-12
    CLI->>UC: PrepareMeetingNotes.execute("2026-04-12")

    UC->>Planner: getMeetingsWithTopics(list="Meetings")
    Planner->>TrelloAPI: GET /lists/{meetingsListId}/cards
    TrelloAPI-->>Planner: Ordered card list
    Planner-->>UC: meetings with grouped topic cards

    UC->>Cal: getMeetings("2026-04-12")
    Cal->>GCal: GET /calendars/primary/events
    GCal-->>Cal: List<Event>
    Cal-->>UC: events with attendee lists

    loop For each meeting card
        alt 1-1 meeting (2 attendees, title "Name / Dima")
            UC->>UC: extract other person's name from event title
            UC->>Notes: findDoc(personName, ONE_ON_ONE)
            Notes->>DriveAPI: GET /files?q=name contains '<name>' in '_Notes/People'
            DriveAPI-->>Notes: Matched file (Latin or Cyrillic name)
            Notes-->>UC: docId + URL
            UC->>Notes: appendAgenda(docId, "2026-04-12", [topic1, topic2, ...])
            Notes->>DocsAPI: GET /documents/{docId}
            DocsAPI-->>Notes: Document body
            Notes->>DocsAPI: POST /documents/{docId}/batchUpdate (Heading1 + paragraphs)
            DocsAPI-->>Notes: OK
            Notes-->>UC: doc URL
        else Group meeting or no doc match
            UC->>Mapping: lookup(meetingTitle)
            alt found in local mapping
                Mapping-->>UC: docName
            else not in mapping
                UC-->>CLI: unresolved(meetingTitle)
                CLI-->>User: "Could not find doc for: <title> — please enter doc name:"
                User->>CLI: types doc name
                CLI->>UC: docName
                UC->>Mapping: save(meetingTitle → docName)
            end
            UC->>Notes: findDoc(docName, GROUP)
            Notes->>DriveAPI: GET /files?q=name contains '<docName>'
            DriveAPI-->>Notes: Matched file
            Notes-->>UC: docId + URL
            UC->>Notes: appendAgenda(docId, "2026-04-12", [topics])
            Notes->>DocsAPI: POST /documents/{docId}/batchUpdate
            DocsAPI-->>Notes: OK
            Notes-->>UC: doc URL
        end
    end

    UC-->>CLI: List of doc URLs (all meetings)
    CLI-->>User: Prints numbered list of Google Doc links
```
