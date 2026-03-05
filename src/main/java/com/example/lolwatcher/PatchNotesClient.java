package com.example.lolwatcher;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatchNotesClient {
    private static final Logger log = LoggerFactory.getLogger(PatchNotesClient.class);

    private static final String DEFAULT_PATCH_NOTES_INDEX_URL = "https://www.leagueoflegends.com/en-us/news/tags/patch-notes/";
    private static final String DEFAULT_RSS_URL = "https://www.leagueoflegends.com/en-us/rss.xml";
    private static final int MAX_HIGHLIGHTS = 4;
    private static final Pattern PATCH_NUMBER_PATTERN = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})");

    private final HttpClient httpClient;
    private final URI patchNotesIndexUri;
    private final URI rssUri;

    public PatchNotesClient() {
        this(DEFAULT_PATCH_NOTES_INDEX_URL, DEFAULT_RSS_URL);
    }

    public PatchNotesClient(String patchNotesIndexUrl, String rssUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.patchNotesIndexUri = URI.create(patchNotesIndexUrl);
        this.rssUri = URI.create(rssUrl);
    }

    public Optional<PatchNotesSummary> fetchLatestSummary(String gameVersion) {
        try {
            Optional<RssItem> itemOpt = findPatchNotesItem(gameVersion);
            if (itemOpt.isEmpty()) {
                return Optional.empty();
            }

            RssItem item = itemOpt.get();
            List<String> highlights = extractHighlights(item.link());
            if (highlights.isEmpty()) {
                return Optional.of(new PatchNotesSummary(item.title(), item.link(), List.of()));
            }
            return Optional.of(new PatchNotesSummary(item.title(), item.link(), highlights));
        } catch (Exception ex) {
            log.warn("Failed to fetch patch notes summary", ex);
            return Optional.empty();
        }
    }

    private Optional<RssItem> findPatchNotesItem(String gameVersion) {
        Optional<RssItem> fromPatchTagPage = findFromPatchTagPage(gameVersion);
        if (fromPatchTagPage.isPresent()) {
            return fromPatchTagPage;
        }

        return findFromRss(gameVersion);
    }

    private Optional<RssItem> findFromPatchTagPage(String gameVersion) {
        try {
            String html = httpGet(patchNotesIndexUri);
            Document doc = Jsoup.parse(html, patchNotesIndexUri.toString());
            Elements anchors = doc.select("a[href*=/news/game-updates/]");
            if (anchors.isEmpty()) {
                return Optional.empty();
            }

            Map<String, String> titleByLink = new LinkedHashMap<>();
            for (Element a : anchors) {
                String href = a.absUrl("href");
                String title = normalize(a.text());
                if (href.isBlank() || title.isBlank()) {
                    continue;
                }
                if (!looksLikePatchTitle(title)) {
                    continue;
                }
                titleByLink.putIfAbsent(href, title);
            }

            if (titleByLink.isEmpty()) {
                return Optional.empty();
            }

            String targetPatch = toPatchNumber(gameVersion).toLowerCase(Locale.ROOT);
            String targetMinor = targetPatch.contains(".")
                    ? targetPatch.substring(targetPatch.indexOf('.'))
                    : targetPatch;

            RssItem latestPatchItem = null;
            for (Map.Entry<String, String> entry : titleByLink.entrySet()) {
                RssItem item = new RssItem(entry.getValue(), entry.getKey());
                if (latestPatchItem == null) {
                    latestPatchItem = item;
                }

                String lowerTitle = entry.getValue().toLowerCase(Locale.ROOT);
                if (lowerTitle.contains(targetPatch) || lowerTitle.contains("patch " + targetPatch)) {
                    return Optional.of(item);
                }

                String titlePatch = extractPatchNumber(lowerTitle);
                if (titlePatch != null && targetMinor.startsWith(".") && titlePatch.endsWith(targetMinor)) {
                    // Graceful fallback when Riot changes major cycle numbering (e.g. 16.x -> 26.x).
                    return Optional.of(item);
                }
            }

            return Optional.ofNullable(latestPatchItem);
        } catch (Exception ex) {
            log.warn("Failed to read patch-notes index page", ex);
            return Optional.empty();
        }
    }

    private Optional<RssItem> findFromRss(String gameVersion) {
        try {
            String xml = httpGet(rssUri);
            Document doc = Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser());
            Elements items = doc.select("item");
            RssItem fallbackPatchItem = null;
            String targetPatch = toPatchNumber(gameVersion).toLowerCase(Locale.ROOT);
            String targetMinor = targetPatch.contains(".")
                    ? targetPatch.substring(targetPatch.indexOf('.'))
                    : targetPatch;

            for (Element item : items) {
                String title = normalize(item.selectFirst("title") == null ? "" : item.selectFirst("title").text());
                String link = normalize(item.selectFirst("link") == null ? "" : item.selectFirst("link").text());
                if (title.isBlank() || link.isBlank()) {
                    continue;
                }

                String lowerTitle = title.toLowerCase(Locale.ROOT);
                boolean looksLikePatch = looksLikePatchTitle(lowerTitle);
                boolean tft = lowerTitle.contains("tft") || lowerTitle.contains("teamfight");
                if (!looksLikePatch || tft) {
                    continue;
                }

                if (fallbackPatchItem == null) {
                    fallbackPatchItem = new RssItem(title, link);
                }

                if (lowerTitle.contains(targetPatch)) {
                    return Optional.of(new RssItem(title, link));
                }

                String titlePatch = extractPatchNumber(lowerTitle);
                if (titlePatch != null && targetMinor.startsWith(".") && titlePatch.endsWith(targetMinor)) {
                    return Optional.of(new RssItem(title, link));
                }
            }

            return Optional.ofNullable(fallbackPatchItem);
        } catch (Exception ex) {
            log.warn("Failed to read patch notes RSS", ex);
            return Optional.empty();
        }
    }

    private List<String> extractHighlights(String articleUrl) throws IOException, InterruptedException {
        String html = httpGet(URI.create(articleUrl));
        Document doc = Jsoup.parse(html, articleUrl);
        doc.select("script, style, noscript").remove();

        List<String> highlights = new ArrayList<>();
        Elements headers = doc.select("h2, h3");
        for (Element header : headers) {
            if (highlights.size() >= MAX_HIGHLIGHTS) {
                break;
            }

            String heading = normalize(header.text());
            if (heading.isBlank() || heading.length() < 2) {
                continue;
            }
            if (isNoiseHeading(heading)) {
                continue;
            }

            String paragraph = firstParagraphAfter(header);
            if (paragraph.isBlank()) {
                continue;
            }

            highlights.add(heading + ": " + shorten(paragraph, 90));
        }

        if (!highlights.isEmpty()) {
            return highlights;
        }

        // Fallback: take first meaningful paragraphs if heading parsing fails.
        for (Element p : doc.select("article p, main p, p")) {
            String text = normalize(p.text());
            if (text.length() < 40) {
                continue;
            }
            highlights.add(shorten(text, 100));
            if (highlights.size() >= 3) {
                break;
            }
        }
        return highlights;
    }

    private String firstParagraphAfter(Element header) {
        Element current = header.nextElementSibling();
        int scanCount = 0;
        while (current != null && scanCount < 8) {
            String tag = current.tagName();
            if ("h2".equals(tag) || "h3".equals(tag)) {
                break;
            }
            if ("p".equals(tag)) {
                return normalize(current.text());
            }
            Element nested = current.selectFirst("p");
            if (nested != null) {
                return normalize(nested.text());
            }
            current = current.nextElementSibling();
            scanCount++;
        }
        return "";
    }

    private String toPatchNumber(String gameVersion) {
        String[] parts = gameVersion.split("\\.");
        if (parts.length >= 2) {
            return parts[0] + "." + parts[1];
        }
        return gameVersion;
    }

    private String httpGet(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "*/*")
                .header("User-Agent", "LoLVersionWatcherBot/1.0")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " for " + uri);
        }
        return response.body();
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private boolean looksLikePatchTitle(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        return (lower.contains("patch") || lower.contains("版本更新")) &&
                !lower.contains("tft") &&
                !lower.contains("teamfight");
    }

    private String extractPatchNumber(String text) {
        Matcher matcher = PATCH_NUMBER_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1) + "." + matcher.group(2);
    }

    private boolean isNoiseHeading(String heading) {
        String lower = heading.toLowerCase(Locale.ROOT);
        return lower.contains("bugfix")
                || lower.contains("bug fix")
                || lower.contains("已知问题")
                || lower.contains("about")
                || lower.contains("目录");
    }

    private String shorten(String text, int maxLen) {
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLen - 1)).trim() + "…";
    }

    private record RssItem(String title, String link) {
    }
}
