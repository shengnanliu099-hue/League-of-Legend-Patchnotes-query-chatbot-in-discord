package com.example.lolwatcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class PbeVersionClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI versionUri;

    public PbeVersionClient(String versionUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
        this.versionUri = URI.create(versionUrl);
    }

    public String fetchLatestPbeVersion() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(versionUri)
                .GET()
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Failed to fetch PBE version. HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode versionNode = root.get("version");
        if (versionNode == null || versionNode.asText().isBlank()) {
            throw new IOException("PBE version response missing 'version' field");
        }
        return versionNode.asText();
    }
}
