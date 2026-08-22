# News Aggregator: NewsController, NewsService, NewsSourceClient

## Описание

Этот модуль реализует тему "Агрегатор новостей": по одному запросу сервис
одновременно обращается к нескольким (3-5) внешним новостным API и
объединяет их ответы в единый список новостей.

Задействованные классы:

- `com.edu.imageconversion.controllers.NewsController` — единственный публичный REST-эндпоинт.
- `com.edu.imageconversion.services.NewsService` — многопоточная логика агрегации (Executor Framework).
- `com.edu.imageconversion.news.NewsSourceClient` — контракт клиента одного источника новостей.
- `com.edu.imageconversion.news.clients.*` — реализации клиента для конкретных источников.
- `com.edu.imageconversion.dto.*` — DTO ответа (`NewsArticleDTO`, `SourceStatusDTO`, `NewsAggregationResponseDTO`).

## Источники новостей

| Клиент | Источник | API |
|---|---|---|
| `NewsApiOrgClient` | NewsAPI.org | `GET https://newsapi.org/v2/everything` |
| `GNewsClient` | GNews | `GET https://gnews.io/api/v4/search` |
| `CurrentsApiClient` | Currents API | `GET https://api.currentsapi.services/v1/search` |
| `GuardianClient` | The Guardian Open Platform | `GET https://content.guardianapis.com/search` |
| `NyTimesClient` | The New York Times Article Search | `GET https://api.nytimes.com/svc/search/v2/articlesearch.json` |

Каждый клиент — Spring-бин (`@Component`), реализующий `NewsSourceClient`.
`NewsService` получает весь список клиентов через constructor injection
(`List<NewsSourceClient>`) и ничего не знает про их внутреннее устройство —
чтобы добавить шестой источник, достаточно реализовать интерфейс и повесить
`@Component`, регистрировать источник в сервисе вручную не нужно.

Общая HTTP-логика (запрос, таймауты, разбор JSON, единообразная обработка
ошибок) вынесена в `AbstractHttpNewsSourceClient`, от которого наследуются
все пять клиентов.

## API-ключи

Каждый источник требует свой ключ, задаваемый в `application.properties`:

```properties
news.source.newsapi.key=
news.source.gnews.key=
news.source.currents.key=
news.source.guardian.key=
news.source.nytimes.key=
```

Если ключ не задан, соответствующий источник не участвует в агрегации:
он просто помечается как неуспешный (`success=false`, понятное сообщение об
ошибке в поле `errorMessage`), а остальные источники продолжают работать —
единая неудача одного API никогда не роняет весь запрос.

## Эндпоинт

```
GET /api/news/aggregate?query={строка}&limit={число, опционально}
```

- `query` — поисковый запрос/ключевые слова (обязательный параметр).
- `limit` — сколько новостей запрашивать у каждого источника (по умолчанию
  берётся `news.aggregation.per-source-limit` из конфигурации).

Пример ответа:

```json
{
  "query": "artificial intelligence",
  "totalSources": 5,
  "totalArticles": 37,
  "elapsedMs": 842,
  "articles": [
    {
      "title": "...",
      "description": "...",
      "url": "...",
      "source": "The Guardian",
      "publishedAt": "2026-08-21T10:15:00Z"
    }
  ],
  "sources": [
    { "source": "NewsAPI.org", "success": true, "articlesFetched": 10, "durationMs": 412, "errorMessage": null },
    { "source": "GNews", "success": false, "articlesFetched": 0, "durationMs": 0, "errorMessage": "GNews: API-ключ не настроен (news.source.gnews.key)" }
  ]
}
```

## Многопоточность: Executor Framework

`NewsService` создаёт собственный именованный `ThreadPoolExecutor`
(потоки `news-fetcher-1`, `news-fetcher-2`, ...), размер пула задаётся
свойством `news.aggregation.pool-size`.

Каждый источник новостей оборачивается в отдельную задачу `Callable`, все
задачи одновременно отправляются в пул через
`ExecutorService.invokeAll(tasks, timeout, TimeUnit.SECONDS)`:

```java
List<Future<SourceFetchResult>> futures =
        executorService.invokeAll(tasks, requestTimeoutSeconds, TimeUnit.SECONDS);
```

Почему именно так, а не `parallelStream()` или "голый" `ExecutorService.submit`
в цикле:

1. **Общий таймаут на весь запрос.** `invokeAll(..., timeout, unit)` сам
   дожидается всех задач, но не дольше указанного времени — источник,
   который завис или отвечает слишком медленно, не блокирует ответ
   пользователю вечно. Незавершённые к этому моменту задачи автоматически
   отменяются (`Future.isCancelled() == true`).
2. **Изоляция ошибок.** Каждая задача сама ловит любое исключение своего
   источника (`fetchFromSource` оборачивает вызов клиента в `try/catch`) —
   падение одного API не прерывает получение результатов с остальных.
3. **Контроль над количеством одновременных запросов наружу.** Размер пула
   фиксирован и настраивается конфигурацией, а не создаётся "по потоку на
   каждый запрос" бесконтрольно.

После того как все задачи завершились (успешно, с ошибкой или по таймауту),
результаты собираются в один список `NewsArticleDTO`, сортируются по дате
публикации (свежие сверху) и возвращаются вместе со статусом по каждому
источнику (`SourceStatusDTO`) — это даёт клиенту API видимость того, что
именно происходило параллельно, а не только конечный список новостей.

Пул потоков корректно останавливается при остановке приложения
(`@PreDestroy` в `NewsService.shutdown()`).
