package com.paulfrmbrn.adapter.out.trello;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfrmbrn.domain.port.out.MeetingBoardPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

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
    public boolean isMeetingListEmpty() {
        try {
            return get("/lists/" + getListId() + "/cards?fields=id").isEmpty();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to check Meetings list: " + e.getMessage(), e);
        }
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
