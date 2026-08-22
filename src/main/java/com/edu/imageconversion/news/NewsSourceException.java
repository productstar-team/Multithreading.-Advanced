package com.edu.imageconversion.news;

/**
 * Ошибка получения новостей от конкретного источника: невалидный/отсутствующий
 * API-ключ, сетевая ошибка, неожиданный формат ответа и т.п.
 * <p>
 * Это checked-исключение специально не наследуется от RuntimeException:
 * оно обязывает каждую реализацию {@link NewsSourceClient} явно обработать
 * ошибку конкретного источника, не уронив при этом всю параллельную агрегацию —
 * см. {@code NewsService}, где падение одного источника не должно
 * останавливать сбор новостей с остальных.
 */
public class NewsSourceException extends Exception {

    public NewsSourceException(String message) {
        super(message);
    }

    public NewsSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
