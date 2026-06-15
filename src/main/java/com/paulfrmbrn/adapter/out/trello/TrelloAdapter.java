package com.paulfrmbrn.adapter.out.trello;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfrmbrn.domain.model.Checklist;
import com.paulfrmbrn.domain.model.MeetingWithTopics;
import com.paulfrmbrn.domain.model.Topic;
import com.paulfrmbrn.domain.port.out.MeetingBoardPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TrelloAdapter implements MeetingBoardPort {

    private static final Logger log = LoggerFactory.getLogger(TrelloAdapter.class);
    private static final String BASE = "https://api.trello.com/1";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern TRELLO_CARD_URL = Pattern.compile("https://trello\\.com/c/([^/?# ]+)");

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
            var cards = get("/lists/" + getListId() + "/cards?fields=name,id,desc&checklists=all&attachments=true");
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
        String id = card.get("id").asText();
        String name = card.get("name").asText();
        JsonNode original = resolveLinkedCard(card);
        if (original != null) {
            id = original.get("id").asText();
            name = original.get("name").asText();
        }
        List<Checklist> checklists = new ArrayList<>();

        JsonNode checklistsNode = card.get("checklists");
        if (checklistsNode != null && checklistsNode.isArray()) {
            for (JsonNode cl : checklistsNode) {
                String clName = cl.get("name").asText();
                Optional<String> lastUnchecked = findFirstUncheckedItem(cl.get("checkItems"));
                checklists.add(new Checklist(clName, lastUnchecked));
            }
        }

        return new Topic(id, name, List.copyOf(checklists));
    }

    @Override
    public void addTopicComment(String topicId, String comment) {
        try {
            String body = MAPPER.writeValueAsString(Map.of(
                    "text", comment,
                    "key", apiKey,
                    "token", apiToken
            ));
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/cards/" + topicId + "/actions/comments"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Trello comment creation failed (" + response.statusCode() + "): " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to add Trello comment: " + e.getMessage(), e);
        }
    }

    private JsonNode resolveLinkedCard(JsonNode card) {
        String cardId = card.get("id").asText();
        String cardName = card.get("name").asText();
        log.debug("Checking card '{}' (id={}) for link indicators", cardName, cardId);

        // 1. Card name is itself a Trello card URL
        String shortLink = trelloShortLink(cardName);

        // 2. Card description contains a Trello card URL
        if (shortLink == null) {
            JsonNode descNode = card.get("desc");
            if (descNode != null && !descNode.asText().isBlank()) {
                log.debug("Card '{}' desc: {}", cardName, descNode.asText());
                shortLink = trelloShortLink(descNode.asText());
            }
        }

        // 3. Attachment URL is a Trello card URL
        if (shortLink == null) {
            JsonNode attachments = card.get("attachments");
            log.debug("Card '{}' attachments: {}", cardName, attachments);
            if (attachments != null && attachments.isArray()) {
                for (JsonNode att : attachments) {
                    JsonNode urlNode = att.get("url");
                    if (urlNode == null) continue;
                    shortLink = trelloShortLink(urlNode.asText());
                    if (shortLink != null) break;
                }
            }
        }

        if (shortLink == null) {
            log.debug("Card '{}' — no Trello link found", cardName);
            return null;
        }

        try {
            JsonNode original = get("/cards/" + shortLink + "?fields=id,name");
            log.debug("Card '{}' resolved to original '{}' (id={})", cardName,
                    original.get("name").asText(), original.get("id").asText());
            return original;
        } catch (Exception e) {
            log.debug("Card '{}' — failed to resolve short link '{}': {}", cardName, shortLink, e.getMessage());
            return null;
        }
    }

    private String trelloShortLink(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = TRELLO_CARD_URL.matcher(text);
        return m.find() ? m.group(1) : null;
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
