package com.edu.imageconversion.news.clients;

import com.edu.imageconversion.dto.NewsArticleDTO;
import com.edu.imageconversion.news.NewsSourceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Клиент источника NewsAPI.org (https://newsapi.org/docs/endpoints/everything).
 */
@Component
public class NewsApiOrgClient extends AbstractHttpNewsSourceClient {

    private static final String SOURCE_NAME = "NewsAPI.org";
    private static final String BASE_URL = "https://newsapi.org/v2/everything";

    @Value("${news.source.newsapi.key:}")
    private String apiKey;

    public NewsApiOrgClient(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    @Override
    public List<NewsArticleDTO> fetchArticles(String query, int limit) throws NewsSourceException {
        if (isApiKeyMissing(apiKey)) {
            throw new NewsSourceException(SOURCE_NAME + ": API-ключ не настроен (news.source.newsapi.key)");
        }

        String url = BASE_URL
                + "?q=" + encode(query)
                + "&sortBy=publishedAt"
                + "&pageSize=" + limit
                + "&apiKey=" + apiKey;

        JsonNode root = getJson(url);

        if (!"ok".equalsIgnoreCase(textOrNull(root, "status"))) {
            throw new NewsSourceException(SOURCE_NAME + ": " + textOrNull(root, "message"));
        }

        List<NewsArticleDTO> articles = new ArrayList<>();
        for (JsonNode item : root.path("articles")) {
            String source = SOURCE_NAME;
            JsonNode sourceNode = item.get("source");
            if (sourceNode != null && sourceNode.hasNonNull("name")) {
                source = sourceNode.get("name").asText();
            }
            articles.add(new NewsArticleDTO(
                    textOrNull(item, "title"),
                    textOrNull(item, "description"),
                    textOrNull(item, "url"),
                    source,
                    textOrNull(item, "publishedAt")
            ));
        }
        return articles;
    }
}
