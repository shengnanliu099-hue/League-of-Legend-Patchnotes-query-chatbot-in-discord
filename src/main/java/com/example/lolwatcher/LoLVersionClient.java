package com.example.lolwatcher;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class LoLVersionClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI versionsUri;

    public LoLVersionClient(String versionsUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.versionsUri = URI.create(versionsUrl);
    }

    public String fetchLatestVersion() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(versionsUri)
                .GET()
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Failed to fetch versions. HTTP " + response.statusCode());
        }

        String[] versions = objectMapper.readValue(response.body(), String[].class);
        if (versions.length == 0) {
            throw new IOException("Versions API returned empty list");
        }

        return versions[0];
    }
}
