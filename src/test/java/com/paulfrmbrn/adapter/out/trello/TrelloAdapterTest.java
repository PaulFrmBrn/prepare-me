package com.paulfrmbrn.adapter.out.trello;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class TrelloAdapterTest {

    @Mock HttpClient http;

    @Test
    void createCard_succeedsWhenBoardAndListFound() throws Exception {
        when(http.send(any(HttpRequest.class), any())).thenAnswer(inv -> {
            HttpRequest req = inv.getArgument(0);
            String url = req.uri().toString();
            if (url.contains("/members/me/boards"))
                return responseWith(200, "[{\"id\":\"board1\",\"name\":\"work\"}]");
            if (url.contains("/boards/board1/lists"))
                return responseWith(200, "[{\"id\":\"list1\",\"name\":\"Meetings\"}]");
            if (url.contains("/cards"))
                return responseWith(200, "{\"id\":\"card1\"}");
            return responseWith(404, "not found");
        });

        var adapter = new TrelloAdapter("key", "token", "work", "Meetings", http);
        adapter.createCard("Meeting: Team Sync"); // should not throw
    }

    @Test
    void createCard_throwsWhenBoardNotFound() throws Exception {
        doReturn(responseWith(200, "[{\"id\":\"board1\",\"name\":\"other board\"}]"))
                .when(http).send(any(HttpRequest.class), any());

        var adapter = new TrelloAdapter("key", "token", "work", "Meetings", http);

        assertThatThrownBy(() -> adapter.createCard("Meeting: Sync"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("board not found: work");
    }

    @Test
    void createCard_throwsWhenListNotFound() throws Exception {
        when(http.send(any(HttpRequest.class), any())).thenAnswer(inv -> {
            HttpRequest req = inv.getArgument(0);
            if (req.uri().toString().contains("/members/me/boards"))
                return responseWith(200, "[{\"id\":\"board1\",\"name\":\"work\"}]");
            return responseWith(200, "[{\"id\":\"list1\",\"name\":\"Backlog\"}]");
        });

        var adapter = new TrelloAdapter("key", "token", "work", "Meetings", http);

        assertThatThrownBy(() -> adapter.createCard("Meeting: Sync"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("list not found: Meetings");
    }

    @Test
    void createCard_throwsOnNon200FromTrello() throws Exception {
        doReturn(responseWith(401, "invalid key"))
                .when(http).send(any(HttpRequest.class), any());

        var adapter = new TrelloAdapter("key", "token", "work", "Meetings", http);

        assertThatThrownBy(() -> adapter.createCard("Meeting: Sync"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("401");
    }

    @Test
    void listIdIsCachedAcrossMultipleCards() throws Exception {
        when(http.send(any(HttpRequest.class), any())).thenAnswer(inv -> {
            HttpRequest req = inv.getArgument(0);
            String url = req.uri().toString();
            if (url.contains("/members/me/boards"))
                return responseWith(200, "[{\"id\":\"board1\",\"name\":\"work\"}]");
            if (url.contains("/boards/board1/lists"))
                return responseWith(200, "[{\"id\":\"list1\",\"name\":\"Meetings\"}]");
            return responseWith(200, "{\"id\":\"card1\"}");
        });

        var adapter = new TrelloAdapter("key", "token", "work", "Meetings", http);
        adapter.createCard("Meeting: Sync");
        adapter.createCard("Meeting: 1:1");

        // boards and lists endpoints called only once despite two cards
        verify(http, times(1)).send(argThat(r -> r.uri().toString().contains("/members/me/boards")), any());
        verify(http, times(1)).send(argThat(r -> r.uri().toString().contains("/boards/board1/lists")), any());
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> responseWith(int status, String body) {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(status);
        when(r.body()).thenReturn(body);
        return r;
    }
}
