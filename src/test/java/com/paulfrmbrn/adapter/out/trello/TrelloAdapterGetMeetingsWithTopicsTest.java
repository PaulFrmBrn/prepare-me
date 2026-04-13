package com.paulfrmbrn.adapter.out.trello;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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
            // cards endpoint
            return response("""
                    [
                      {"name":"Meeting: Ivan / Dima"},
                      {"name":"1:1 check-in"},
                      {"name":"performance review"},
                      {"name":"Meeting: Weekly Sync"},
                      {"name":"sprint planning"}
                    ]
                    """);
        });

        var adapter = new TrelloAdapter("key", "token", "work", "Meetings", http);
        var result = adapter.getMeetingsWithTopics();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Meeting: Ivan / Dima");
        assertThat(result.get(0).topics()).containsExactly("1:1 check-in", "performance review");
        assertThat(result.get(1).name()).isEqualTo("Meeting: Weekly Sync");
        assertThat(result.get(1).topics()).containsExactly("sprint planning");
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
            return response("[{\"name\":\"Meeting: Standup\"},{\"name\":\"Meeting: 1:1 with Bob\"}]");
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

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(String body) {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn(body);
        return r;
    }
}
