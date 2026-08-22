package com.edu.imageconversion.controllers;

import com.edu.imageconversion.dto.NewsAggregationResponseDTO;
import com.edu.imageconversion.services.NewsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST-контроллер агрегатора новостей.
 * <p>
 * Единственный публичный эндпоинт {@code GET /api/news/aggregate} по запросу
 * одновременно обращается к нескольким новостным API (см. {@code NewsService}
 * и реализации {@code NewsSourceClient}) и возвращает объединённый результат
 * одним JSON-ответом.
 */
@RestController
@RequestMapping("/api/news")
public class NewsController {

    private static final Logger logger = LogManager.getLogger(NewsController.class);

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @Operation(
            summary = "Агрегировать новости с нескольких источников",
            description = "Параллельно опрашивает несколько новостных API (Executor Framework) по заданному " +
                    "поисковому запросу и возвращает объединённый список новостей вместе со статусом " +
                    "выполнения по каждому источнику."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Новости успешно агрегированы",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "400", description = "Некорректные параметры запроса", content = @Content),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера", content = @Content)
    })
    @GetMapping("/aggregate")
    public ResponseEntity<NewsAggregationResponseDTO> aggregateNews(
            @Parameter(description = "Поисковый запрос / ключевые слова", example = "artificial intelligence")
            @RequestParam("query") String query,
            @Parameter(description = "Максимум новостей на источник (по умолчанию берётся из конфигурации)")
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        long startTime = System.currentTimeMillis();
        try {
            NewsAggregationResponseDTO response = newsService.aggregateNews(query, limit);

            long endTime = System.currentTimeMillis();
            logger.info("News aggregation request for query='{}' took {} ms", query, (endTime - startTime));

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request for news aggregation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Error during news aggregation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
