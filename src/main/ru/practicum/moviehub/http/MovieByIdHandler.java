package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;

public class MovieByIdHandler extends BaseHttpHandler {
    private final MoviesStore store;

    public MovieByIdHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    protected void handleGet(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] pathParts = path.split("/");

        // Проверяем формат пути: /movies/{id}
        if (pathParts.length != 3) {
            sendResponse(exchange, 404, "{\"error\":\"Не найдено\"}");
            return;
        }

        String idParam = pathParts[2];

        // Проверяем, что ID - число
        long id;
        try {
            id = Long.parseLong(idParam);
        } catch (NumberFormatException e) {
            sendResponse(exchange, 400, "{\"error\":\"Некорректный ID\"}");
            return;
        }

        // Ищем фильм
        Movie movie = store.getMovieById(id);
        if (movie == null) {
            sendResponse(exchange, 404, "{\"error\":\"Фильм не найден\"}");
            return;
        }

        // Возвращаем фильм
        String response = GSON.toJson(movie);
        sendResponse(exchange, 200, response);
    }

    @Override
    protected void handleDelete(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] pathParts = path.split("/");

        // Проверяем формат пути: /movies/{id}
        if (pathParts.length != 3) {
            sendResponse(exchange, 404, "{\"error\":\"Не найдено\"}");
            return;
        }

        String idParam = pathParts[2];

        // Проверяем, что ID - число
        long id;
        try {
            id = Long.parseLong(idParam);
        } catch (NumberFormatException e) {
            sendResponse(exchange, 400, "{\"error\":\"Некорректный ID\"}");
            return;
        }

        // Удаляем фильм
        boolean deleted = store.deleteMovie(id);
        if (!deleted) {
            sendResponse(exchange, 404, "{\"error\":\"Фильм не найден\"}");
            return;
        }

        // Возвращаем успешный ответ без тела
        sendResponse(exchange, 204);
    }
}
