package com.edu.imageconversion.services;

import com.edu.imageconversion.dto.NewsAggregationResponseDTO;
import com.edu.imageconversion.dto.NewsArticleDTO;
import com.edu.imageconversion.dto.SourceStatusDTO;
import com.edu.imageconversion.news.NewsSourceClient;
import jakarta.annotation.PreDestroy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Сервис агрегации новостей: одновременно опрашивает несколько внешних
 * новостных API (3-5 разных источников, каждый реализован своим
 * {@link NewsSourceClient}) и объединяет их ответы в единый список.
 * <p>
 * Распараллеливание построено на классическом Executor Framework:
 * под капотом создаётся именованный {@link ThreadPoolExecutor}, а каждый
 * источник опрашивается как отдельная задача {@link Callable}, отправленная
 * в пул через {@link ExecutorService#invokeAll(java.util.Collection, long, TimeUnit)}.
 * Такой выбор (вместо, например, простого {@code parallelStream()}) даёт три вещи,
 * важные именно для агрегатора внешних API:
 * <ul>
 *     <li>общий таймаут на весь запрос — медленный/зависший источник не блокирует ответ вечно;</li>
 *     <li>изоляцию ошибок — падение одного источника не прерывает остальные задачи в пуле;</li>
 *     <li>контроль над размером пула — количество одновременных HTTP-соединений
 *     наружу ограничено и настраивается через {@code application.properties}.</li>
 * </ul>
 */
@Service
public class NewsService {

    private static final Logger logger = LogManager.getLogger(NewsService.class);

    private final List<NewsSourceClient> sourceClients;
    private final ThreadPoolExecutor executorService;
    private final int requestTimeoutSeconds;
    private final int defaultPerSourceLimit;

    public NewsService(
            List<NewsSourceClient> sourceClients,
            @Value("${news.aggregation.pool-size:5}") int poolSize,
            @Value("${news.aggregation.timeout-seconds:10}") int requestTimeoutSeconds,
            @Value("${news.aggregation.per-source-limit:10}") int defaultPerSourceLimit
    ) {
        this.sourceClients = sourceClients;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.defaultPerSourceLimit = defaultPerSourceLimit;

        // Явно собранный ThreadPoolExecutor вместо Executors.newFixedThreadPool(...),
        // чтобы дать потокам понятные имена (видно в логах и дампах потоков) и
        // не хранить в очереди задачи сверх количества источников - каждой задаче
        // соответствует ровно один источник новостей.
        AtomicInteger threadCounter = new AtomicInteger(1);
        this.executorService = new ThreadPoolExecutor(
                poolSize,
                poolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable, "news-fetcher-" + threadCounter.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
        );
        this.executorService.allowCoreThreadTimeOut(true);

        logger.info("NewsService initialized with {} source(s), pool size {}, timeout {}s",
                sourceClients.size(), poolSize, requestTimeoutSeconds);
    }

    /**
     * Параллельно опрашивает все зарегистрированные источники новостей по заданному
     * запросу и возвращает объединённый результат.
     *
     * @param query поисковый запрос/ключевые слова (например, название компании или темы)
     * @param limit сколько новостей запрашивать у каждого источника (если null - берётся значение по умолчанию)
     * @return агрегированный ответ со списком новостей и статусом по каждому источнику
     */
    public NewsAggregationResponseDTO aggregateNews(String query, Integer limit) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Параметр query не может быть пустым");
        }
        int perSourceLimit = (limit == null || limit <= 0) ? defaultPerSourceLimit : limit;

        long startTime = System.currentTimeMillis();

        List<Callable<SourceFetchResult>> tasks = new ArrayList<>(sourceClients.size());
        for (NewsSourceClient client : sourceClients) {
            tasks.add(() -> fetchFromSource(client, query, perSourceLimit));
        }

        List<Future<SourceFetchResult>> futures;
        try {
            // invokeAll блокирует вызывающий поток (поток обработки HTTP-запроса
            // контроллера), пока не завершатся ВСЕ задачи, либо пока не истечёт
            // общий таймаут - после чего недошедшие задачи отменяются автоматически.
            futures = executorService.invokeAll(tasks, requestTimeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Агрегация новостей была прервана", e);
        }

        List<NewsArticleDTO> aggregatedArticles = new ArrayList<>();
        List<SourceStatusDTO> sourceStatuses = new ArrayList<>(futures.size());

        for (int i = 0; i < futures.size(); i++) {
            NewsSourceClient client = sourceClients.get(i);
            Future<SourceFetchResult> future = futures.get(i);

            if (future.isCancelled()) {
                logger.warn("[{}] fetch cancelled - exceeded overall timeout of {}s",
                        client.getSourceName(), requestTimeoutSeconds);
                sourceStatuses.add(new SourceStatusDTO(
                        client.getSourceName(), false, 0, requestTimeoutSeconds * 1000L,
                        "Превышен общий таймаут запроса (" + requestTimeoutSeconds + "s)"));
                continue;
            }

            try {
                SourceFetchResult result = future.get();
                if (result.error() == null) {
                    aggregatedArticles.addAll(result.articles());
                    sourceStatuses.add(new SourceStatusDTO(
                            client.getSourceName(), true, result.articles().size(), result.durationMs(), null));
                } else {
                    sourceStatuses.add(new SourceStatusDTO(
                            client.getSourceName(), false, 0, result.durationMs(), result.error()));
                }
            } catch (ExecutionException e) {
                logger.error("[{}] unexpected failure while fetching news", client.getSourceName(), e.getCause());
                sourceStatuses.add(new SourceStatusDTO(
                        client.getSourceName(), false, 0, 0, "Внутренняя ошибка: " + e.getCause().getMessage()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                sourceStatuses.add(new SourceStatusDTO(
                        client.getSourceName(), false, 0, 0, "Поток прерван при ожидании результата"));
            }
        }

        // Свежие новости - в начале списка. Источники, не отдающие publishedAt,
        // не теряются - просто уходят в конец сортировки.
        aggregatedArticles.sort(
                Comparator.comparing(NewsArticleDTO::publishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
        );

        long elapsedMs = System.currentTimeMillis() - startTime;
        logger.info("Aggregated {} articles from {}/{} sources in {} ms",
                aggregatedArticles.size(), sourceStatuses.stream().filter(SourceStatusDTO::success).count(),
                sourceClients.size(), elapsedMs);

        return new NewsAggregationResponseDTO(
                query,
                sourceClients.size(),
                aggregatedArticles.size(),
                elapsedMs,
                aggregatedArticles,
                sourceStatuses
        );
    }

    /**
     * Выполняется в одном из потоков пула. Ловит {@code Throwable} специально
     * широко: любая ошибка конкретного клиента (в том числе неожиданный
     * RuntimeException при разборе JSON) не должна "убивать" задачу способом,
     * из-за которого {@code invokeAll} потерял бы результаты остальных источников.
     */
    private SourceFetchResult fetchFromSource(NewsSourceClient client, String query, int limit) {
        long taskStart = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();
        try {
            logger.debug("[{}] fetching on thread {}", client.getSourceName(), threadName);
            List<NewsArticleDTO> articles = client.fetchArticles(query, limit);
            long duration = System.currentTimeMillis() - taskStart;
            logger.info("[{}] fetched {} article(s) in {} ms (thread {})",
                    client.getSourceName(), articles.size(), duration, threadName);
            return SourceFetchResult.success(articles, duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - taskStart;
            logger.warn("[{}] failed after {} ms: {}", client.getSourceName(), duration, e.getMessage());
            return SourceFetchResult.failure(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), duration);
        }
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down news-fetcher thread pool");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }

    /**
     * Внутренний результат одной задачи пула: либо список новостей, либо описание ошибки.
     */
    private record SourceFetchResult(List<NewsArticleDTO> articles, String error, long durationMs) {
        static SourceFetchResult success(List<NewsArticleDTO> articles, long durationMs) {
            return new SourceFetchResult(articles, null, durationMs);
        }

        static SourceFetchResult failure(String error, long durationMs) {
            return new SourceFetchResult(List.of(), error, durationMs);
        }
    }
}
