package com.edu.imageconversion.dto;

import java.util.List;

/**
 * Итоговый ответ агрегатора новостей: объединённый список новостей
 * со всех опрошенных источников плюс метаданные о самом запросе
 * (сколько источников отработали успешно/с ошибкой, сколько заняло в сумме).
 *
 * @param query          поисковый запрос, по которому собирались новости
 * @param totalSources   сколько источников было опрошено параллельно
 * @param totalArticles  итоговое количество новостей после объединения
 * @param elapsedMs      суммарное время выполнения запроса (от старта до объединения результатов), мс
 * @param articles       объединённый список новостей со всех источников
 * @param sources        статус выполнения по каждому источнику
 */
public record NewsAggregationResponseDTO(
        String query,
        int totalSources,
        int totalArticles,
        long elapsedMs,
        List<NewsArticleDTO> articles,
        List<SourceStatusDTO> sources
) {
}
