package com.edu.imageconversion.news.clients;

import com.edu.imageconversion.news.NewsSourceClient;
import com.edu.imageconversion.news.NewsSourceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Базовый класс для клиентов новостных API поверх {@link HttpClient} (JDK 11+).
 * <p>
 * Забирает на себя всё общее: конфигурацию HTTP-клиента с таймаутами,
 * URL-кодирование параметров, выполнение GET-запроса и разбор JSON в дерево
 * {@link JsonNode}, а также единообразную обёртку любых ошибок в
 * {@link NewsSourceException}, чтобы падение одного источника не рушило
 * параллельный сбор новостей с остальных источников в {@code NewsService}.
 */
public abstract class AbstractHttpNewsSourceClient implements NewsSourceClient {

    private static final Logger logger = LogManager.getLogger(AbstractHttpNewsSourceClient.class);

    protected final HttpClient httpClient;
    protected final ObjectMapper objectMapper;

    protected AbstractHttpNewsSourceClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Проверяет, настроен ли у клиента API-ключ. Клиенты без ключа не считаются
     * ошибкой конфигурации всего приложения — они просто пропускаются при агрегации,
     * с понятным сообщением в {@link SourceStatusDTO}.
     */
    protected boolean isApiKeyMissing(String apiKey) {
        return apiKey == null || apiKey.isBlank() || apiKey.startsWith("${");
    }

    protected String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Выполняет GET-запрос по указанному URL и возвращает тело ответа, разобранное
     * в дерево JSON. Любая сетевая/HTTP/парсинг-ошибка оборачивается в
     * {@link NewsSourceException} с именем источника в сообщении.
     */
    protected JsonNode getJson(String url) throws NewsSourceException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            logger.debug("[{}] -> GET {}", getSourceName(), url);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                throw new NewsSourceException(getSourceName() + ": HTTP " + response.statusCode()
                        + " - " + truncate(response.body()));
            }
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new NewsSourceException(getSourceName() + ": request failed - " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NewsSourceException(getSourceName() + ": request interrupted", e);
        }
    }

    protected String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    private String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }
}
