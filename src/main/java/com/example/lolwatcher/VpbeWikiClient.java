package com.example.lolwatcher;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

public class VpbeWikiClient {
    private static final Logger log = LoggerFactory.getLogger(VpbeWikiClient.class);

    private final HttpClient httpClient;
    private final URI wikiUri;

    public VpbeWikiClient(String wikiUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.wikiUri = URI.create(wikiUrl);
    }

    public Optional<VpbeWikiInfo> fetchInfo() {
        try {
            String html = httpGet(wikiUri);
            Document doc = Jsoup.parse(html, wikiUri.toString());

            String title = normalize(selectMeta(doc, "og:title"));
            if (title.isBlank()) {
                title = normalize(doc.title());
            }
            if (title.isBlank()) {
                title = "VPBE";
            }

            String lastUpdated = normalize(selectText(doc, "#footer-info-lastmod"));
            if (lastUpdated.isBlank()) {
                lastUpdated = normalize(selectMeta(doc, "article:modified_time"));
            }
            if (lastUpdated.isBlank()) {
                Element time = doc.selectFirst("time");
                lastUpdated = normalize(time == null ? "" : time.text());
            }
            if (lastUpdated.isBlank()) {
                lastUpdated = "Unknown";
            }

            return Optional.of(new VpbeWikiInfo(title, lastUpdated, wikiUri.toString()));
        } catch (Exception ex) {
            log.warn("Failed to fetch VPBE wiki info", ex);
            return Optional.empty();
        }
    }

    private String httpGet(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "text/html,application/xhtml+xml")
                .header("User-Agent", "LoLVersionWatcherBot/1.0")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " for " + uri);
        }
        return response.body();
    }

    private String selectMeta(Document doc, String property) {
        Element el = doc.selectFirst("meta[property=" + property + "]");
        return el == null ? "" : el.attr("content");
    }

    private String selectText(Document doc, String selector) {
        Element el = doc.selectFirst(selector);
        return el == null ? "" : el.text();
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }
}
