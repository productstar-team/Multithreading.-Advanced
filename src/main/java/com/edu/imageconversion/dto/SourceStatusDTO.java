package com.edu.imageconversion.dto;

/**
 * Отчёт о выполнении запроса к одному конкретному источнику новостей.
 * Позволяет клиенту API увидеть, какие источники ответили успешно,
 * какие упали по ошибке/таймауту, и сколько времени заняла каждая задача,
 * выполнявшаяся в пуле потоков.
 *
 * @param source          имя источника
 * @param success         true, если источник успешно вернул новости
 * @param articlesFetched количество новостей, полученных от источника (0, если была ошибка)
 * @param durationMs      время выполнения запроса к источнику, мс
 * @param errorMessage    описание ошибки, если success == false; иначе null
 */
public record SourceStatusDTO(
        String source,
        boolean success,
        int articlesFetched,
        long durationMs,
        String errorMessage
) {
}
