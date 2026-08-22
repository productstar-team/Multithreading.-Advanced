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
 * Клиент источника Currents API (https://currentsapi.services/en/docs/search).
 */
@Component
public class CurrentsApiClient extends AbstractHttpNewsSourceClient {

    private static final String SOURCE_NAME = "Currents API";
    private static final String BASE_URL = "https://api.currentsapi.services/v1/search";

    @Value("${news.source.currents.key:}")
    private String apiKey;

    public CurrentsApiClient(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    @Override
    public List<NewsArticleDTO> fetchArticles(String query, int limit) throws NewsSourceException {
        if (isApiKeyMissing(apiKey)) {
            throw new NewsSourceException(SOURCE_NAME + ": API-ключ не настроен (news.source.currents.key)");
        }

        String url = BASE_URL
                + "?keywords=" + encode(query)
                + "&apiKey=" + apiKey;

        JsonNode root = getJson(url);

        if (!"ok".equalsIgnoreCase(textOrNull(root, "status"))) {
            throw new NewsSourceException(SOURCE_NAME + ": статус ответа - " + textOrNull(root, "status"));
        }

        List<NewsArticleDTO> articles = new ArrayList<>();
        int count = 0;
        for (JsonNode item : root.path("news")) {
            if (count >= limit) {
                break;
            }
            articles.add(new NewsArticleDTO(
                    textOrNull(item, "title"),
                    textOrNull(item, "description"),
                    textOrNull(item, "url"),
                    SOURCE_NAME,
                    textOrNull(item, "published")
            ));
            count++;
        }
        return articles;
    }
}
