package com.paulfrmbrn.adapter.out.google.auth;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpBackOffIOExceptionHandler;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.ExponentialBackOff;
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
import java.util.concurrent.TimeUnit;

public class GoogleAuthProvider {

    private static final List<String> SCOPES = List.of(
            CalendarScopes.CALENDAR_READONLY,
            DriveScopes.DRIVE_READONLY,
            "https://www.googleapis.com/auth/documents"
    );

    private static final int CONNECT_TIMEOUT_MS = (int) TimeUnit.SECONDS.toMillis(30);

    /**
     * Read timeout. The library default is 20s, which is not enough for
     * {@code documents.get} on long-lived notes documents (hundreds of KB of content
     * expand into a much larger JSON response).
     */
    private static final int READ_TIMEOUT_MS = (int) TimeUnit.MINUTES.toMillis(3);

    private final String credentialsFile;
    private final Path tokensDir;

    private HttpTransport transport;
    private Credential credential;

    public GoogleAuthProvider(String credentialsFile, String tokensDir) {
        this.credentialsFile = credentialsFile;
        this.tokensDir = Path.of(tokensDir);
    }

    public synchronized HttpTransport getTransport() throws GeneralSecurityException, IOException {
        if (transport == null) {
            transport = GoogleNetHttpTransport.newTrustedTransport();
        }
        return transport;
    }

    public synchronized Credential getCredential() throws IOException, GeneralSecurityException {
        if (credential != null) {
            return credential;
        }
        var jsonFactory = GsonFactory.getDefaultInstance();

        Files.createDirectories(tokensDir);

        try (var in = new FileInputStream(credentialsFile)) {
            var secrets = GoogleClientSecrets.load(jsonFactory, new InputStreamReader(in));
            var flow = new GoogleAuthorizationCodeFlow.Builder(getTransport(), jsonFactory, secrets, SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(tokensDir.toFile()))
                    .setAccessType("offline")
                    .build();
            var receiver = new LocalServerReceiver.Builder().setPort(8888).build();
            credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
            return credential;
        }
    }

    /**
     * Credential plus transport-level timeouts. Idempotent requests (GET) are also retried with
     * exponential backoff on connection errors; mutating requests are not, because a request that
     * timed out may still have been applied server-side and replaying it would duplicate the write.
     */
    public HttpRequestInitializer requestInitializer() throws IOException, GeneralSecurityException {
        var cred = getCredential();
        return request -> {
            cred.initialize(request);
            request.setConnectTimeout(CONNECT_TIMEOUT_MS);
            request.setReadTimeout(READ_TIMEOUT_MS);
            if ("GET".equals(request.getRequestMethod())) {
                request.setIOExceptionHandler(
                        new HttpBackOffIOExceptionHandler(new ExponentialBackOff()));
            }
        };
    }
}
