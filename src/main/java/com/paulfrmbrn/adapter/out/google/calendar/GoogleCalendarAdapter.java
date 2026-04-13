package com.paulfrmbrn.adapter.out.google.calendar;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.paulfrmbrn.adapter.out.google.auth.GoogleAuthProvider;
import com.paulfrmbrn.domain.model.Meeting;
import com.paulfrmbrn.domain.port.out.CalendarPort;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public class GoogleCalendarAdapter implements CalendarPort {

    private final GoogleAuthProvider auth;

    public GoogleCalendarAdapter(GoogleAuthProvider auth) {
        this.auth = auth;
    }

    @Override
    public List<Meeting> getMeetings(LocalDate date) {
        try {
            var transport = GoogleNetHttpTransport.newTrustedTransport();
            var service = new Calendar.Builder(transport, GsonFactory.getDefaultInstance(), auth.getCredential())
                    .setApplicationName("PrepareMe")
                    .build();

            var zone = ZoneId.systemDefault();
            var timeMin = new DateTime(date.atStartOfDay(zone).toInstant().toEpochMilli());
            var timeMax = new DateTime(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli());

            var events = service.events().list("primary")
                    .setTimeMin(timeMin)
                    .setTimeMax(timeMax)
                    .setSingleEvents(true)
                    .setOrderBy("startTime")
                    .execute();

            var items = events.getItems();
            if (items == null) return List.of();

            return items.stream()
                    .filter(e -> e.getSummary() != null)
                    .filter(e -> e.getStart().getDateTime() != null) // skip all-day blocks
                    .map(e -> new Meeting(e.getSummary()))
                    .toList();

        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("Failed to fetch calendar events: " + e.getMessage(), e);
        }
    }
}
