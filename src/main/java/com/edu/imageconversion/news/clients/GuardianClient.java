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
 * Клиент источника The Guardian Open Platform (https://open-platform.theguardian.com/documentation/search).
 */
@Component
public class GuardianClient extends AbstractHttpNewsSourceClient {

    private static final String SOURCE_NAME = "The Guardian";
    private static final String BASE_URL = "https://content.guardianapis.com/search";

    @Value("${news.source.guardian.key:}")
    private String apiKey;

    public GuardianClient(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    @Override
    public List<NewsArticleDTO> fetchArticles(String query, int limit) throws NewsSourceException {
        if (isApiKeyMissing(apiKey)) {
            throw new NewsSourceException(SOURCE_NAME + ": API-ключ не настроен (news.source.guardian.key)");
        }

        String url = BASE_URL
                + "?q=" + encode(query)
                + "&page-size=" + limit
                + "&show-fields=trailText"
                + "&api-key=" + apiKey;

        JsonNode root = getJson(url).path("response");

        if (!"ok".equalsIgnoreCase(textOrNull(root, "status"))) {
            throw new NewsSourceException(SOURCE_NAME + ": статус ответа - " + textOrNull(root, "status"));
        }

        List<NewsArticleDTO> articles = new ArrayList<>();
        for (JsonNode item : root.path("results")) {
            String description = null;
            JsonNode fields = item.get("fields");
            if (fields != null) {
                description = textOrNull(fields, "trailText");
            }
            articles.add(new NewsArticleDTO(
                    textOrNull(item, "webTitle"),
                    description,
                    textOrNull(item, "webUrl"),
                    SOURCE_NAME,
                    textOrNull(item, "webPublicationDate")
            ));
        }
        return articles;
    }
}
