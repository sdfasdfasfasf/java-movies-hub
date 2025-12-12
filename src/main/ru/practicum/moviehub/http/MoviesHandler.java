package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;
import ru.practicum.moviehub.validation.MovieValidator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MoviesHandler extends BaseHttpHandler {
    private final MoviesStore store;

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    protected void handleGet(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        // Если путь содержит больше сегментов, чем просто /movies,
        // значит это запрос к /movies/{id} или /movies/{id}/something
        // и он должен обрабатываться другим обработчиком
        if (!path.equals("/movies")) {
            ErrorResponse error = new ErrorResponse("Не найдено");
            sendResponse(exchange, 404, GSON.toJson(error));
            return;
        }

        // Получаем query параметры
        String query = exchange.getRequestURI().getQuery();
        List<Movie> movies;

        if (query != null && query.contains("year=")) {
            // Извлекаем параметр year
            String yearParam = null;
            String[] queryParams = query.split("&");
            for (String param : queryParams) {
                if (param.startsWith("year=")) {
                    yearParam = param.substring(5);
                    break;
                }
            }

            if (yearParam != null) {
                try {
                    int year = Integer.parseInt(yearParam);
                    movies = store.getMoviesByYear(year);
                } catch (NumberFormatException e) {
                    ErrorResponse error = new ErrorResponse("Некорректный параметр запроса - 'year'");
                    sendResponse(exchange, 400, GSON.toJson(error));
                    return;
                }
            } else {
                movies = store.getAllMovies();
            }
        } else {
            movies = store.getAllMovies();
        }

        // Возвращаем фильмы
        String moviesJson = GSON.toJson(movies);
        sendResponse(exchange, 200, moviesJson);
    }

    @Override
    protected void handlePost(HttpExchange exchange) throws IOException {
        // Проверяем Content-Type
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("application/json")) {
            ErrorResponse error = new ErrorResponse("Unsupported Media Type");
            sendResponse(exchange, 415, GSON.toJson(error));
            return;
        }

        // Читаем тело запроса
        StringBuilder requestBody = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }
        }

        try {
            // Парсим JSON
            Movie movie = GSON.fromJson(requestBody.toString(), Movie.class);

            // Валидация
            List<String> validationErrors = MovieValidator.validate(movie);
            if (!validationErrors.isEmpty()) {
                ErrorResponse errorResponse = new ErrorResponse("Ошибка валидации", validationErrors);
                sendResponse(exchange, 422, GSON.toJson(errorResponse));
                return;
            }

            // Сохраняем фильм
            Movie createdMovie = store.addMovie(movie);

            // Возвращаем созданный фильм
            String response = GSON.toJson(createdMovie);
            sendResponse(exchange, 201, response);

        } catch (Exception e) {
            // Некорректный JSON
            ErrorResponse error = new ErrorResponse("Некорректный JSON");
            sendResponse(exchange, 400, GSON.toJson(error));
        }
    }
}