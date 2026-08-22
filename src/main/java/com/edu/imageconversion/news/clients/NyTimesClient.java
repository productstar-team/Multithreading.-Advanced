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
 * Клиент источника The New York Times Article Search API
 * (https://developer.nytimes.com/docs/articlesearch-product/1/overview).
 */
@Component
public class NyTimesClient extends AbstractHttpNewsSourceClient {

    private static final String SOURCE_NAME = "The New York Times";
    private static final String BASE_URL = "https://api.nytimes.com/svc/search/v2/articlesearch.json";

    @Value("${news.source.nytimes.key:}")
    private String apiKey;

    public NyTimesClient(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    @Override
    public List<NewsArticleDTO> fetchArticles(String query, int limit) throws NewsSourceException {
        if (isApiKeyMissing(apiKey)) {
            throw new NewsSourceException(SOURCE_NAME + ": API-ключ не настроен (news.source.nytimes.key)");
        }

        String url = BASE_URL
                + "?q=" + encode(query)
                + "&sort=newest"
                + "&api-key=" + apiKey;

        JsonNode root = getJson(url).path("response");

        List<NewsArticleDTO> articles = new ArrayList<>();
        int count = 0;
        for (JsonNode item : root.path("docs")) {
            if (count >= limit) {
                break;
            }
            String title = null;
            JsonNode headline = item.get("headline");
            if (headline != null) {
                title = textOrNull(headline, "main");
            }
            articles.add(new NewsArticleDTO(
                    title,
                    textOrNull(item, "abstract"),
                    textOrNull(item, "web_url"),
                    SOURCE_NAME,
                    textOrNull(item, "pub_date")
            ));
            count++;
        }
        return articles;
    }
}
