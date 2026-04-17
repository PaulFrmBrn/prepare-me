package com.paulfrmbrn.adapter.out.trello;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfrmbrn.domain.model.Checklist;
import com.paulfrmbrn.domain.model.MeetingWithTopics;
import com.paulfrmbrn.domain.model.Topic;
import com.paulfrmbrn.domain.port.out.MeetingBoardPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TrelloAdapter implements MeetingBoardPort {

    private static final String BASE = "https://api.trello.com/1";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final String apiToken;
    private final String boardName;
    private final String listName;
    private final HttpClient http;

    private String cachedListId;

    public TrelloAdapter(String apiKey, String apiToken, String boardName, String listName) {
        this(apiKey, apiToken, boardName, listName, HttpClient.newHttpClient());
    }

    TrelloAdapter(String apiKey, String apiToken, String boardName, String listName, HttpClient http) {
        this.apiKey = apiKey;
        this.apiToken = apiToken;
        this.boardName = boardName;
        this.listName = listName;
        this.http = http;
    }

    @Override
    public void createCard(String name) {
        try {
            String body = MAPPER.writeValueAsString(Map.of(
                    "name", name,
                    "idList", getListId(),
                    "key", apiKey,
                    "token", apiToken
            ));
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/cards"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Trello card creation failed (" + response.statusCode() + "): " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to create Trello card: " + e.getMessage(), e);
        }
    }

    @Override
    public List<MeetingWithTopics> getMeetingsWithTopics() {
        try {
            var cards = get("/lists/" + getListId() + "/cards?fields=name,id&checklists=all");
            List<MeetingWithTopics> result = new ArrayList<>();
            String currentMeeting = null;
            List<Topic> currentTopics = null;

            for (JsonNode card : cards) {
                String name = card.get("name").asText();
                if (name.startsWith("Meeting: ")) {
                    if (currentMeeting != null) {
                        result.add(new MeetingWithTopics(currentMeeting, List.copyOf(currentTopics)));
                    }
                    currentMeeting = name;
                    currentTopics = new ArrayList<>();
                } else if (currentMeeting != null) {
                    currentTopics.add(parseTopicCard(card));
                }
            }
            if (currentMeeting != null) {
                result.add(new MeetingWithTopics(currentMeeting, List.copyOf(currentTopics)));
            }
            return result;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to fetch meetings with topics: " + e.getMessage(), e);
        }
    }

    private Topic parseTopicCard(JsonNode card) {
        String name = card.get("name").asText();
        List<Checklist> checklists = new ArrayList<>();

        JsonNode checklistsNode = card.get("checklists");
        if (checklistsNode != null && checklistsNode.isArray()) {
            for (JsonNode cl : checklistsNode) {
                String clName = cl.get("name").asText();
                Optional<String> lastUnchecked = findFirstUncheckedItem(cl.get("checkItems"));
                checklists.add(new Checklist(clName, lastUnchecked));
            }
        }

        return new Topic(name, List.copyOf(checklists));
    }

    private Optional<String> findFirstUncheckedItem(JsonNode items) {
        if (items == null || !items.isArray()) return Optional.empty();

        String firstName = null;
        double firstPos = Double.MAX_VALUE;

        for (JsonNode item : items) {
            if ("incomplete".equals(item.get("state").asText())) {
                double pos = item.get("pos").asDouble();
                if (pos < firstPos) {
                    firstPos = pos;
                    firstName = item.get("name").asText();
                }
            }
        }

        return Optional.ofNullable(firstName);
    }

    private String getListId() throws IOException, InterruptedException {
        if (cachedListId != null) return cachedListId;

        String boardId = findBoardId();
        cachedListId = findListId(boardId);
        return cachedListId;
    }

    private String findBoardId() throws IOException, InterruptedException {
        var boards = get("/members/me/boards?fields=name,id");
        for (JsonNode board : boards) {
            if (boardName.equals(board.get("name").asText())) {
                return board.get("id").asText();
            }
        }
        throw new RuntimeException("Trello board not found: " + boardName);
    }

    private String findListId(String boardId) throws IOException, InterruptedException {
        var lists = get("/boards/" + boardId + "/lists?fields=name,id");
        for (JsonNode list : lists) {
            if (listName.equals(list.get("name").asText())) {
                return list.get("id").asText();
            }
        }
        throw new RuntimeException("Trello list not found: " + listName);
    }

    private JsonNode get(String path) throws IOException, InterruptedException {
        String url = BASE + path + (path.contains("?") ? "&" : "?") + "key=" + apiKey + "&token=" + apiToken;
        var request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Trello request failed (" + response.statusCode() + "): " + response.body());
        }
        return MAPPER.readTree(response.body());
    }
}
