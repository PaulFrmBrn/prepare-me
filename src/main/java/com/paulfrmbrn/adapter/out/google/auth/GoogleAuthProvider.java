package com.paulfrmbrn.adapter.out.google.auth;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.drive.DriveScopes;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;

public class GoogleAuthProvider {

    private static final List<String> SCOPES = List.of(
            CalendarScopes.CALENDAR_READONLY,
            DriveScopes.DRIVE_READONLY,
            "https://www.googleapis.com/auth/documents"
    );

    private final String credentialsFile;
    private final Path tokensDir;

    public GoogleAuthProvider(String credentialsFile, String tokensDir) {
        this.credentialsFile = credentialsFile;
        this.tokensDir = Path.of(tokensDir);
    }

    public Credential getCredential() throws IOException, GeneralSecurityException {
        var transport = GoogleNetHttpTransport.newTrustedTransport();
        var jsonFactory = GsonFactory.getDefaultInstance();

        Files.createDirectories(tokensDir);

        try (var in = new FileInputStream(credentialsFile)) {
            var secrets = GoogleClientSecrets.load(jsonFactory, new InputStreamReader(in));
            var flow = new GoogleAuthorizationCodeFlow.Builder(transport, jsonFactory, secrets, SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(tokensDir.toFile()))
                    .setAccessType("offline")
                    .build();
            var receiver = new LocalServerReceiver.Builder().setPort(8888).build();
            return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        }
    }
}
