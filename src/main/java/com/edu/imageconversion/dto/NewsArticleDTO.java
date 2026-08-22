package com.edu.imageconversion.dto;

/**
 * Единая (унифицированная) модель новости, к которой приводятся ответы
 * всех источников новостей, прежде чем попасть в общий агрегированный ответ.
 *
 * @param title       заголовок новости
 * @param description краткое описание/анонс
 * @param url         ссылка на полный текст новости
 * @param source      человекочитаемое имя источника (например, "NewsAPI.org")
 * @param publishedAt дата публикации в исходном формате, отданном источником (ISO-8601, если источник его предоставляет)
 */
public record NewsArticleDTO(
        String title,
        String description,
        String url,
        String source,
        String publishedAt
) {
}
