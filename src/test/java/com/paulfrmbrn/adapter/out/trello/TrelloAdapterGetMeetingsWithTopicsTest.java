package com.paulfrmbrn.adapter.out.trello;

import com.paulfrmbrn.domain.model.Checklist;
import com.paulfrmbrn.domain.model.Topic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrelloAdapterGetMeetingsWithTopicsTest {

    @Mock HttpClient http;

    @Test
    void groupsTopicCardsUnderPrecedingMeetingCard() throws Exception {
        when(http.send(any(HttpRequest.class), any())).thenAnswer(inv -> {
            HttpRequest req = inv.getArgument(0);
            String url = req.uri().toString();
            if (url.contains("/members/me/boards"))
                return response("[{\"id\":\"b1\",\"name\":\"work\"}]");
            if (url.contains("/boards/b1/lists"))
                return response("[{\"id\":\"l1\",\"name\":\"Meetings\"}]");
            return response("""
                    [
                      {"name":"Meeting: Ivan / Dima","checklists":[]},
                      {"name":"1:1 check-in","checklists":[]},
                      {"name":"performance review","checklists":[]},
                      {"name":"Meeting: Weekly Sync","checklists":[]},
                      {"name":"sprint planning","checklists":[]}
                    ]
                    """);
        });

        var adapter = new TrelloAdapter("key", "token", "work", "Meetings", http);
        var result = adapter.getMeetingsWithTopics();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Meeting: Ivan / Dima");
        assertThat(result.get(0).topics()).containsExactly(
                new Topic("1:1 check-in", List.of()),
                new Topic("performance review", List.of())
        );
        assertThat(result.get(1).name()).isEqualTo("Meeting: Weekly Sync");
        assertThat(result.get(1).topics()).containsExactly(new Topic("sprint planning", List.of()));
    }

    @Test
    void meetingWithNoTopicsHasEmptyTopicList() throws Exception {
        when(http.send(any(HttpRequest.class), any())).thenAnswer(inv -> {
            HttpRequest req = inv.getArgument(0);
            String url = req.uri().toString();
            if (url.contains("/members/me/boards"))
                return response("[{\"id\":\"b1\",\"name\":\"work\"}]");
            if (url.contains("/boards/b1/lists"))
                return response("[{\"id\":\"l1\",\"name\":\"Meetings\"}]");
            return response("""
                    [
                      {"name":"Meeting: Standup","checklists":[]},
                      {"name":"Meeting: 1:1 with Bob","checklists":[]}
                    ]
                    """);
        });

        var adapter = new TrelloAdapter("key", "token", "work", "Meetings", http);
        var result = adapter.getMeetingsWithTopics();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).topics()).isEmpty();
        assertThat(result.get(1).topics()).isEmpty();
    }

    @Test
    void emptyListReturnsEmptyResult() throws Exception {
        when(http.send(any(HttpRequest.class), any())).thenAnswer(inv -> {
            HttpRequest req = inv.getArgument(0);
            String url = req.uri().toString();
            if (url.contains("/members/me/boards"))
                return response("[{\"id\":\"b1\",\"name\":\"work\"}]");
            if (url.contains("/boards/b1/lists"))
                return response("[{\"id\":\"l1\",\"name\":\"Meetings\"}]");
            return response("[]");
        });

        var adapter = new TrelloAdapter("key", "token", "work", "Meetings", http);
        assertThat(adapter.getMeetingsWithTopics()).isEmpty();
    }

    @Test
    void topicCard_withNamedChecklist_picksFirstUncheckedItemByPos() throws Exception {
        when(http.send(any(HttpRequest.class), any())).thenAnswer(inv -> {
            HttpRequest req = inv.getArgument(0);
            String url = req.uri().toString();
            if (url.contains("/members/me/boards"))
                return response("[{\"id\":\"b1\",\"name\":\"work\"}]");
            if (url.contains("/boards/b1/lists"))
                return response("[{\"id\":\"l1\",\"name\":\"Meetings\"}]");
            return response("""
                    [
                      {"name":"Meeting: Standup","checklists":[]},
                      {"name":"Deploy","checklists":[
                        {"name":"Prep","checkItems":[
                          {"name":"Run tests","state":"complete","pos":1000},
                          {"name":"Notify team","state":"incomplete","pos":2000},
                          {"name":"Update docs","state":"incomplete","pos":3000}
                        ]}
                      ]}
                    ]
                    """);
        });

        var adapter = new TrelloAdapter("key", "token", "work", "Meetings", http);
        var result = adapter.getMeetingsWithTopics();

        assertThat(result).hasSize(1);
        List<Topic> topics = result.get(0).topics();
        assertThat(topics).hasSize(1);
        Topic topic = topics.get(0);
        assertThat(topic.name()).isEqualTo("Deploy");
        assertThat(topic.checklists()).containsExactly(
                new Checklist("Prep", Optional.of("Notify team"))
        );
    }

    @Test
    void topicCard_allItemsChecked_returnsEmptyUnchecked() throws Exception {
        when(http.send(any(HttpRequest.class), any())).thenAnswer(inv -> {
            HttpRequest req = inv.getArgument(0);
            String url = req.uri().toString();
            if (url.contains("/members/me/boards"))
                return response("[{\"id\":\"b1\",\"name\":\"work\"}]");
            if (url.contains("/boards/b1/lists"))
                return response("[{\"id\":\"l1\",\"name\":\"Meetings\"}]");
            return response("""
                    [
                      {"name":"Meeting: Standup","checklists":[]},
                      {"name":"Deploy","checklists":[
                        {"name":"Checklist","checkItems":[
                          {"name":"Run tests","state":"complete","pos":1000},
                          {"name":"Notify team","state":"complete","pos":2000}
                        ]}
                      ]}
                    ]
                    """);
        });

        var adapter = new TrelloAdapter("key", "token", "work", "Meetings", http);
        var result = adapter.getMeetingsWithTopics();

        Topic topic = result.get(0).topics().get(0);
        assertThat(topic.checklists()).containsExactly(
                new Checklist("Checklist", Optional.empty())
        );
    }

    @Test
    void topicCard_noChecklists_returnsEmptyChecklistList() throws Exception {
        when(http.send(any(HttpRequest.class), any())).thenAnswer(inv -> {
            HttpRequest req = inv.getArgument(0);
            String url = req.uri().toString();
            if (url.contains("/members/me/boards"))
                return response("[{\"id\":\"b1\",\"name\":\"work\"}]");
            if (url.contains("/boards/b1/lists"))
                return response("[{\"id\":\"l1\",\"name\":\"Meetings\"}]");
            return response("""
                    [
                      {"name":"Meeting: Standup","checklists":[]},
                      {"name":"Quick note","checklists":[]}
                    ]
                    """);
        });

        var adapter = new TrelloAdapter("key", "token", "work", "Meetings", http);
        var result = adapter.getMeetingsWithTopics();

        Topic topic = result.get(0).topics().get(0);
        assertThat(topic.name()).isEqualTo("Quick note");
        assertThat(topic.checklists()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(String body) {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn(body);
        return r;
    }
}
