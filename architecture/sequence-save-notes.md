
# Sequence Diagram — Save Notes (phase 4)

```mermaid
sequenceDiagram
    actor User
    participant CLI as Prepare Me CLI
    participant UC as SaveMeetingNotes
    participant Planner as Trello
    participant Cal as Google Calendar
    participant Mapping as Doc Mapping
    participant Notes as Google Notes

    User->>CLI: save-notes --date 2026-04-12
    CLI->>UC: execute("2026-04-12")

    UC->>Planner: getMeetingsWithTopics()
    Planner-->>UC: meetings with topic cards (incl. card IDs; link cards resolve to original card ID)

    UC->>Cal: getMeetings("2026-04-12")
    Cal-->>UC: calendar events

    loop For each meeting
        UC->>Mapping: resolveDocName(meetingTitle)
        alt mapping found
            UC->>Notes: findDoc(docName)
            Notes-->>UC: DocRef
        else MissingDocMappingException
            alt 1-on-1 ("Name / Dima" title)
                UC->>Notes: findDoc(notesDir/People/personName)
                Notes-->>UC: DocRef
            else Group meeting
                UC-->>CLI: skip with hint to add mapping
            end
        end

        UC->>Notes: readTopicNotes(doc, date, meetingName)
        Notes-->>UC: List<TopicContent>

        loop For each topic with non-empty notes
            UC->>Planner: addTopicComment(topicId, bodyText)
        end
    end

    UC-->>CLI: meeting name → comments posted count
    CLI-->>User: numbered list of results
```

Note over User,CLI: (Fill in Notes) User manually writes notes under topic headings<br/>in Google Docs between Create Agenda and Save Notes
```
