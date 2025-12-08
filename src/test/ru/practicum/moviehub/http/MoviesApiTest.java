package ru.practicum.moviehub.http;

import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.*;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static ru.practicum.moviehub.http.BaseHttpHandler.GSON;

public class MoviesApiTest {
    private static final int CONNECTION_TIMEOUT_SECONDS = 2;
    private static final int SERVER_PORT = 8080;

    private static final String BASE_URL = "http://localhost:" + SERVER_PORT;
    private static final String MOVIES_ENDPOINT = BASE_URL + "/movies";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String FULL_CONTENT_TYPE = "application/json; charset=UTF-8";

    private static final int STATUS_OK = 200;
    private static final int STATUS_CREATED = 201;
    private static final int STATUS_NO_CONTENT = 204;
    private static final int STATUS_BAD_REQUEST = 400;
    private static final int STATUS_NOT_FOUND = 404;
    private static final int STATUS_UNPROCESSABLE_ENTITY = 422;

    private static MoviesServer server;
    private HttpClient client;

    @BeforeEach
    void beforeEach() throws IOException, InterruptedException {
        // Запускаем сервер перед каждым тестом
        server = new MoviesServer(new MoviesStore(), SERVER_PORT);
        server.start();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECTION_TIMEOUT_SECONDS))
                .build();
    }

    @AfterEach
    void afterEach() throws IOException, InterruptedException {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        // Создаём запрос
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(MOVIES_ENDPOINT))
                .GET()
                .build();

        // Отправляем запрос
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Проверяем код ответа
        assertEquals(STATUS_OK, resp.statusCode(), "GET /movies должен вернуть " + STATUS_OK);

        // Проверяем заголовок Content-Type
        String contentTypeHeaderValue = resp.headers().firstValue(CONTENT_TYPE_HEADER).orElse("");
        assertEquals(FULL_CONTENT_TYPE, contentTypeHeaderValue,
                CONTENT_TYPE_HEADER + " должен содержать формат данных и кодировку");

        // Проверяем, что был возвращён массив
        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");

        // Проверяем, что массив пустой
        assertEquals("[]", body, "Ожидается пустой JSON-массив");
    }

    @Test
    void getMovies_whenMoviesExist_returnsMoviesList() throws Exception {
        // Создаем несколько фильмов
        String movie1Json = """
                {
                    "title": "Интерстеллар",
                    "year": 2014
                }
                """;

        String movie2Json = """
                {
                    "title": "Довод",
                    "year": 2020
                }
                """;

        // Добавляем фильмы
        HttpRequest postReq1 = HttpRequest.newBuilder()
                .uri(URI.create(MOVIES_ENDPOINT))
                .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(movie1Json, StandardCharsets.UTF_8))
                .build();

        HttpRequest postReq2 = HttpRequest.newBuilder()
                .uri(URI.create(MOVIES_ENDPOINT))
                .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(movie2Json, StandardCharsets.UTF_8))
                .build();

        client.send(postReq1, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        client.send(postReq2, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Запрашиваем все фильмы
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(MOVIES_ENDPOINT))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(getReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Проверяем ответ
        assertEquals(STATUS_OK, resp.statusCode(), "Должен вернуть " + STATUS_OK);

        // Проверяем, что вернулись оба фильма
        String body = resp.body();
        List<Movie> movies = GSON.fromJson(body, new TypeToken<List<Movie>>() {
        }.getType());

        assertEquals(2, movies.size(), "Должен вернуть 2 фильма");

        // Проверяем, что оба фильма присутствуют
        boolean hasInterstellar = movies.stream().anyMatch(m -> "Интерстеллар".equals(m.getTitle()));
        boolean hasTenet = movies.stream().anyMatch(m -> "Довод".equals(m.getTitle()));

        assertTrue(hasInterstellar, "Должен содержать Интерстеллар");
        assertTrue(hasTenet, "Должен содержать Довод");
    }

    @Test
    void postMovies_withValidData_createsMovie() throws Exception {
        // Подготавливаем JSON для создания фильма
        String movieJson = """
                {
                    "title": "Интерстеллар",
                    "year": 2014
                }
                """;

        // Создаём POST-запрос
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(MOVIES_ENDPOINT))
                .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(movieJson, StandardCharsets.UTF_8))
                .build();

        // Отправляем запрос
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Проверяем код ответа (должен быть 201 Created)
        assertEquals(STATUS_CREATED, resp.statusCode(), "POST /movies должен вернуть " + STATUS_CREATED + " Created");

        // Проверяем заголовок Content-Type
        String contentTypeHeaderValue = resp.headers().firstValue(CONTENT_TYPE_HEADER).orElse("");
        assertEquals(FULL_CONTENT_TYPE, contentTypeHeaderValue,
                CONTENT_TYPE_HEADER + " должен содержать формат данных и кодировку");

        // Парсим ответ
        String responseBody = resp.body();
        assertNotNull(responseBody, "Тело ответа не должно быть null");

        // Проверяем, что в ответе есть ID
        assertTrue(responseBody.contains("\"id\""), "Ответ должен содержать ID фильма");
        assertTrue(responseBody.contains("\"title\":\"Интерстеллар\""), "Ответ должен содержать title");
        assertTrue(responseBody.contains("\"year\":2014"), "Ответ должен содержать year");

        // Дополнительно проверяем, что фильм добавился в список
        // Делаем GET запрос
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(MOVIES_ENDPOINT))
                .GET()
                .build();

        HttpResponse<String> getResp = client.send(getReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String getBody = getResp.body();

        // Проверяем, что список не пустой
        assertNotEquals("[]", getBody, "Список фильмов не должен быть пустым после добавления");
        assertTrue(getBody.contains("Интерстеллар"), "Список должен содержать добавленный фильм");
    }

    @Test
    void postMovies_withEmptyTitle_returnsValidationError() throws Exception {
        String movieJson = """
                {
                    "title": "",
                    "year": 2020
                }
                """;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(MOVIES_ENDPOINT))
                .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(movieJson, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(STATUS_UNPROCESSABLE_ENTITY, resp.statusCode(), "Должен вернуть " + STATUS_UNPROCESSABLE_ENTITY + " при пустом title");

        String body = resp.body();
        assertTrue(body.contains("Ошибка валидации"), "Должна быть ошибка валидации");
        assertTrue(body.contains("название не должно быть пустым"), "Должна быть детальная ошибка");
    }

    @Test
    void getMovieById_whenExists_returnsMovie() throws Exception {
        // Сначала создаем фильм
        String movieJson = """
                {
                    "title": "Интерстеллар",
                    "year": 2014
                }
                """;

        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(MOVIES_ENDPOINT))
                .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(movieJson, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> postResp = client.send(postReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(STATUS_CREATED, postResp.statusCode());

        // Парсим ответ, чтобы получить ID созданного фильма
        Movie createdMovie = GSON.fromJson(postResp.body(), Movie.class);
        long movieId = createdMovie.getId();

        // Теперь запрашиваем фильм по ID
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(MOVIES_ENDPOINT + "/" + movieId))
                .GET()
                .build();

        HttpResponse<String> getResp = client.send(getReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Проверяем ответ
        assertEquals(STATUS_OK, getResp.statusCode(), "Должен вернуть " + STATUS_OK + " при существующем ID");

        // Проверяем заголовок Content-Type
        String contentTypeHeaderValue = getResp.headers().firstValue(CONTENT_TYPE_HEADER).orElse("");
        assertEquals(FULL_CONTENT_TYPE, contentTypeHeaderValue,
                CONTENT_TYPE_HEADER + " должен содержать формат данных и кодировку");

        // Проверяем тело ответа
        String body = getResp.body();
        Movie movie = GSON.fromJson(body, Movie.class);
        assertEquals(movieId, movie.getId(), "ID должно совпадать");
        assertEquals("Интерстеллар", movie.getTitle(), "Title должно совпадать");
        assertEquals(2014, movie.getYear(), "Year должно совпадать");
    }

    @Test
    void getMovieById_whenNotFound_returns404() throws Exception {
        // Запрашиваем несуществующий ID
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(MOVIES_ENDPOINT + "/999"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(getReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Проверяем ответ
        assertEquals(STATUS_NOT_FOUND, resp.statusCode(), "Должен вернуть " + STATUS_NOT_FOUND + " при несуществующем ID");

        // Проверяем структуру ошибки
        String body = resp.body();
        assertTrue(body.contains("\"error\""), "Должно содержать поле error");
    }

    @Test
    void getMovieById_withNonNumericId_returns400() throws Exception {
        // Запрашиваем с нечисловым ID
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(MOVIES_ENDPOINT + "/abc"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(getReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Проверяем ответ
        assertEquals(STATUS_BAD_REQUEST, resp.statusCode(), "Должен вернуть " + STATUS_BAD_REQUEST + " при нечисловом ID");

        // Проверяем структуру ошибки
        String body = resp.body();
        assertTrue(body.contains("\"error\""), "Должно содержать поле error");
    }

    @Test
    void deleteMovie_whenExists_returns204() throws Exception {
        // Сначала создаем фильм
        String movieJson = """
                {
                    "title": "Интерстеллар",
                    "year": 2014
                }
                """;

        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(MOVIES_ENDPOINT))
                .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(movieJson, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> postResp = client.send(postReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(STATUS_CREATED, postResp.statusCode());

        Movie createdMovie = GSON.fromJson(postResp.body(), Movie.class);
        long movieId = createdMovie.getId();

        // Удаляем фильм
        HttpRequest deleteReq = HttpRequest.newBuilder()
                .uri(URI.create(MOVIES_ENDPOINT + "/" + movieId))
                .DELETE()
                .build();

        HttpResponse<String> deleteResp = client.send(deleteReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Проверяем ответ
        assertEquals(STATUS_NO_CONTENT, deleteResp.statusCode(), "Должен вернуть " + STATUS_NO_CONTENT + " при успешном удалении");

        // Проверяем, что фильм действительно удален
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(MOVIES_ENDPOINT + "/" + movieId))
                .GET()
                .build();

        HttpResponse<String> getResp = client.send(getReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(STATUS_NOT_FOUND, getResp.statusCode(), "Фильм должен быть удален");
    }

    @Test
    void deleteMovie_whenNotFound_returns404() throws Exception {
        // Пытаемся удалить несуществующий фильм
        HttpRequest deleteReq = HttpRequest.newBuilder()
                .uri(URI.create(MOVIES_ENDPOINT + "/999"))
                .DELETE()
                .build();

        HttpResponse<String> resp = client.send(deleteReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Проверяем ответ
        assertEquals(STATUS_NOT_FOUND, resp.statusCode(), "Должен вернуть " + STATUS_NOT_FOUND + " при несуществующем ID");

        // Проверяем структуру ошибки
        String body = resp.body();
        assertTrue(body.contains("\"error\""), "Должно содержать поле error");
    }

    @Test
    void deleteMovie_withNonNumericId_returns400() throws Exception {
        // Пытаемся удалить с нечисловым ID
        HttpRequest deleteReq = HttpRequest.newBuilder()
                .uri(URI.create(MOVIES_ENDPOINT + "/abc"))
                .DELETE()
                .build();

        HttpResponse<String> resp = client.send(deleteReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Проверяем ответ
        assertEquals(STATUS_BAD_REQUEST, resp.statusCode(), "Должен вернуть " + STATUS_BAD_REQUEST + " при нечисловом ID");

        // Проверяем структуру ошибки
        String body = resp.body();
        assertTrue(body.contains("\"error\""), "Должно содержать поле error");
    }

    @Test
    void getMoviesByYear_whenMoviesExist_returnsFilteredList() throws Exception {
        // Создаем фильмы разных годов
        String movie1Json = """
                {
                    "title": "Интерстеллар",
                    "year": 2014
                }
                """;

        String movie2Json = """
                {
                    "title": "Довод",
                    "year": 2020
                }
                """;

        String movie3Json = """
                {
                    "title": "Начало",
                    "year": 2010
                }
                """;

        // Добавляем фильмы
        HttpRequest postReq1 = HttpRequest.newBuilder()
                .uri(URI.create(MOVIES_ENDPOINT))
                .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(movie1Json, StandardCharsets.UTF_8))
                .build();

        HttpRequest postReq2 = HttpRequest.newBuilder()
                .uri(URI.create(MOVIES_ENDPOINT))
                .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(movie2Json, StandardCharsets.UTF_8))
                .build();

        HttpRequest postReq3 = HttpRequest.newBuilder()
                .uri(URI.create(MOVIES_ENDPOINT))
                .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(movie3Json, StandardCharsets.UTF_8))
                .build();

        client.send(postReq1, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        client.send(postReq2, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        client.send(postReq3, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Запрашиваем фильмы 2014 года
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(MOVIES_ENDPOINT + "?year=2014"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(getReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Проверяем ответ
        assertEquals(STATUS_OK, resp.statusCode(), "Должен вернуть " + STATUS_OK);

        // Проверяем, что вернулся только один фильм - Интерстеллар
        String body = resp.body();
        List<Movie> movies = GSON.fromJson(body, new TypeToken<List<Movie>>() {
        }.getType());

        assertEquals(1, movies.size(), "Должен вернуть 1 фильм");
        assertEquals("Интерстеллар", movies.get(0).getTitle(), "Должен вернуть Интерстеллар");
        assertEquals(2014, movies.get(0).getYear(), "Год должен быть 2014");
    }

    @Test
    void getMoviesByYear_whenNoMatches_returnsEmptyArray() throws Exception {
        // Создаем фильм
        String movieJson = """
                {
                    "title": "Интерстеллар",
                    "year": 2014
                }
                """;

        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(MOVIES_ENDPOINT))
                .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(movieJson, StandardCharsets.UTF_8))
                .build();

        client.send(postReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Запрашиваем фильмы другого года
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(MOVIES_ENDPOINT + "?year=2020"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(getReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Проверяем ответ
        assertEquals(STATUS_OK, resp.statusCode(), "Должен вернуть " + STATUS_OK);

        // Проверяем, что вернулся пустой массив
        String body = resp.body().trim();
        assertEquals("[]", body, "Должен вернуть пустой массив");
    }

    @Test
    void getMoviesByYear_withInvalidYear_returns400() throws Exception {
        // Запрашиваем с нечисловым годом
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(MOVIES_ENDPOINT + "?year=abc"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(getReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Проверяем ответ
        assertEquals(STATUS_BAD_REQUEST, resp.statusCode(), "Должен вернуть " + STATUS_BAD_REQUEST + " при нечисловом году");

        // Проверяем структуру ошибки
        String body = resp.body();
        assertTrue(body.contains("\"error\""), "Должно содержать поле error");
    }
}