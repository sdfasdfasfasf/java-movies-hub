package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MoviesApiTest {
    private static final String BASE_URL = "http://localhost:8080";
    private static final String MOVIES_ENDPOINT = BASE_URL + "/movies";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String FULL_CONTENT_TYPE = "application/json; charset=UTF-8";
    private static final Gson GSON = new GsonBuilder().create();

    private static final int STATUS_OK = 200;
    private static final int STATUS_CREATED = 201;
    private static final int STATUS_NO_CONTENT = 204;
    private static final int STATUS_BAD_REQUEST = 400;
    private static final int STATUS_NOT_FOUND = 404;
    private static final int STATUS_METHOD_NOT_ALLOWED = 405;
    private static final int STATUS_UNPROCESSABLE_ENTITY = 422;
    private static final int STATUS_UNSUPPORTED_MEDIA_TYPE = 415;

    private static final int CONNECTION_TIMEOUT_SECONDS = 2;
    private static final int SERVER_PORT = 8080;

    private static final String MOVIE_TITLE_1 = "Интерстеллар";
    private static final int MOVIE_YEAR_1 = 2014;
    private static final String MOVIE_TITLE_2 = "Довод";
    private static final int MOVIE_YEAR_2 = 2020;
    private static final String MOVIE_TITLE_3 = "Начало";
    private static final int MOVIE_YEAR_3 = 2010;

    private static final String EMPTY_TITLE_MOVIE_JSON = "{\"title\":\"\",\"year\":2020}";
    private static final String VALID_MOVIE_JSON = "{\"title\":\"Интерстеллар\",\"year\":2014}";

    private MoviesServer server;
    private HttpClient client;

    @BeforeEach
    void setUp() {
        server = new MoviesServer(new MoviesStore(), SERVER_PORT);
        server.start();
        client = createHttpClient();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    private HttpClient createHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(CONNECTION_TIMEOUT_SECONDS)).build();
    }

    private HttpRequest createGetRequest(String url) {
        return HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
    }

    private HttpRequest createPostRequest(String url, String jsonBody) {
        return HttpRequest.newBuilder().uri(URI.create(url)).header(CONTENT_TYPE_HEADER, CONTENT_TYPE_JSON).POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)).build();
    }

    private HttpRequest createDeleteRequest(String url) {
        return HttpRequest.newBuilder().uri(URI.create(url)).DELETE().build();
    }

    private HttpResponse<String> sendRequest(HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> getMovies() throws Exception {
        HttpRequest request = createGetRequest(MOVIES_ENDPOINT);
        return sendRequest(request);
    }

    private HttpResponse<String> getMovieById(long id) throws Exception {
        HttpRequest request = createGetRequest(MOVIES_ENDPOINT + "/" + id);
        return sendRequest(request);
    }

    private HttpResponse<String> getMoviesByYear(int year) throws Exception {
        HttpRequest request = createGetRequest(MOVIES_ENDPOINT + "?year=" + year);
        return sendRequest(request);
    }

    private HttpResponse<String> createMovie(String title, int year) throws Exception {
        String movieJson = String.format("{\"title\":\"%s\",\"year\":%d}", title, year);
        HttpRequest request = createPostRequest(MOVIES_ENDPOINT, movieJson);
        return sendRequest(request);
    }

    private HttpResponse<String> createMovie(String json) throws Exception {
        HttpRequest request = createPostRequest(MOVIES_ENDPOINT, json);
        return sendRequest(request);
    }

    private HttpResponse<String> deleteMovie(long id) throws Exception {
        HttpRequest request = createDeleteRequest(MOVIES_ENDPOINT + "/" + id);
        return sendRequest(request);
    }

    private void assertStatusCode(HttpResponse<String> response, int expectedStatusCode, String message) {
        assertEquals(expectedStatusCode, response.statusCode(), message);
    }

    private void assertContentType(HttpResponse<String> response) {
        String contentTypeHeaderValue = response.headers().firstValue(CONTENT_TYPE_HEADER).orElse("");
        assertEquals(FULL_CONTENT_TYPE, contentTypeHeaderValue, "Content-Type должен содержать формат данных и кодировку");
    }

    private void assertJsonArray(HttpResponse<String> response) {
        String body = response.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"), "Ожидается JSON-массив");
    }

    private void assertErrorResponse(HttpResponse<String> response) {
        String body = response.body();
        assertTrue(body.contains("\"error\""), "Должно содержать поле error");
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpResponse<String> response = getMovies();

        assertStatusCode(response, STATUS_OK, "GET /movies должен вернуть 200");
        assertContentType(response);
        assertJsonArray(response);

        assertEquals("[]", response.body().trim(), "Ожидается пустой JSON-массив");
    }

    @Test
    void getMovies_whenMoviesExist_returnsMoviesList() throws Exception {
        createMovie(MOVIE_TITLE_1, MOVIE_YEAR_1);
        createMovie(MOVIE_TITLE_2, MOVIE_YEAR_2);

        HttpResponse<String> response = getMovies();

        assertStatusCode(response, STATUS_OK, "Должен вернуть 200");

        String body = response.body();
        ListOfMoviesTypeToken typeToken = new ListOfMoviesTypeToken();
        List<Movie> movies = GSON.fromJson(body, typeToken.getType());

        assertEquals(2, movies.size(), "Должен вернуть 2 фильма");

        boolean hasInterstellar = movies.stream().anyMatch(m -> MOVIE_TITLE_1.equals(m.getTitle()));
        boolean hasTenet = movies.stream().anyMatch(m -> MOVIE_TITLE_2.equals(m.getTitle()));

        assertTrue(hasInterstellar, "Должен содержать " + MOVIE_TITLE_1);
        assertTrue(hasTenet, "Должен содержать " + MOVIE_TITLE_2);
    }

    @Test
    void postMovies_withValidData_createsMovie() throws Exception {
        HttpResponse<String> response = createMovie(MOVIE_TITLE_1, MOVIE_YEAR_1);

        assertStatusCode(response, STATUS_CREATED, "POST /movies должен вернуть 201 Created");
        assertContentType(response);

        String responseBody = response.body();
        assertNotNull(responseBody, "Тело ответа не должно быть null");

        assertTrue(responseBody.contains("\"id\""), "Ответ должен содержать ID фильма");
        assertTrue(responseBody.contains("\"title\":\"" + MOVIE_TITLE_1 + "\""), "Ответ должен содержать title");
        assertTrue(responseBody.contains("\"year\":" + MOVIE_YEAR_1), "Ответ должен содержать year");

        HttpResponse<String> getResponse = getMovies();
        String getBody = getResponse.body();

        assertNotEquals("[]", getBody, "Список фильмов не должен быть пустым после добавления");
        assertTrue(getBody.contains(MOVIE_TITLE_1), "Список должен содержать добавленный фильм");
    }

    @Test
    void postMovies_withEmptyTitle_returnsValidationError() throws Exception {
        HttpResponse<String> response = createMovie(EMPTY_TITLE_MOVIE_JSON);

        assertStatusCode(response, STATUS_UNPROCESSABLE_ENTITY, "Должен вернуть 422 при пустом title");

        String body = response.body();
        ErrorResponse errorResponse = GSON.fromJson(body, ErrorResponse.class);

        assertEquals("Ошибка валидации", errorResponse.getError(), "Должна быть ошибка валидации");
        assertNotNull(errorResponse.getDetails(), "Details не должен быть null");
        assertTrue(errorResponse.getDetails().contains("название не должно быть пустым"),
                "Должна быть детальная ошибка");
    }

    @Test
    void postMovies_withTooLongTitle_returnsValidationError() throws Exception {
        String longTitle = "A".repeat(101);
        String movieJson = String.format("{\"title\":\"%s\",\"year\":2020}", longTitle);

        HttpResponse<String> response = createMovie(movieJson);

        assertStatusCode(response, STATUS_UNPROCESSABLE_ENTITY, "Должен вернуть 422 при слишком длинном title");
        assertErrorResponse(response);

        String body = response.body();
        assertTrue(body.contains("Ошибка валидации"), "Должна быть ошибка валидации");
        assertTrue(body.contains("название не должно превышать 100 символов"), "Должна быть детальная ошибка");
    }

    @Test
    void postMovies_withInvalidYear_returnsValidationError() throws Exception {
        String movieJson = "{\"title\":\"Фильм\",\"year\":1800}";

        HttpResponse<String> response = createMovie(movieJson);

        assertStatusCode(response, STATUS_UNPROCESSABLE_ENTITY, "Должен вернуть 422 при неверном year");
        assertErrorResponse(response);

        String body = response.body();
        assertTrue(body.contains("Ошибка валидации"), "Должна быть ошибка валидации");
        assertTrue(body.contains("год должен быть не менее 1888"), "Должна быть детальная ошибка");
    }

    @Test
    void postMovies_withWrongContentType_returns415() throws Exception {
        String movieJson = VALID_MOVIE_JSON;
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(MOVIES_ENDPOINT)).header(CONTENT_TYPE_HEADER, "text/plain").POST(HttpRequest.BodyPublishers.ofString(movieJson, StandardCharsets.UTF_8)).build();

        HttpResponse<String> response = sendRequest(request);

        assertStatusCode(response, STATUS_UNSUPPORTED_MEDIA_TYPE, "Должен вернуть 415 при неправильном Content-Type");
        assertErrorResponse(response);
    }

    @Test
    void postMovies_withInvalidJson_returns400() throws Exception {
        String invalidJson = "{ это не json }";
        HttpRequest request = createPostRequest(MOVIES_ENDPOINT, invalidJson);

        HttpResponse<String> response = sendRequest(request);

        assertStatusCode(response, STATUS_BAD_REQUEST, "Должен вернуть 400 при некорректном JSON");
        assertErrorResponse(response);
    }

    @Test
    void getMovieById_whenExists_returnsMovie() throws Exception {
        HttpResponse<String> createResponse = createMovie(MOVIE_TITLE_1, MOVIE_YEAR_1);
        assertStatusCode(createResponse, STATUS_CREATED, "Должен вернуть 201 при создании фильма");

        Movie createdMovie = GSON.fromJson(createResponse.body(), Movie.class);
        long movieId = createdMovie.getId();

        HttpResponse<String> response = getMovieById(movieId);

        assertStatusCode(response, STATUS_OK, "Должен вернуть 200 при существующем ID");
        assertContentType(response);

        String body = response.body();
        Movie movie = GSON.fromJson(body, Movie.class);
        assertEquals(movieId, movie.getId(), "ID должно совпадать");
        assertEquals(MOVIE_TITLE_1, movie.getTitle(), "Title должно совпадать");
        assertEquals(MOVIE_YEAR_1, movie.getYear(), "Year должно совпадать");
    }

    @Test
    void getMovieById_whenNotFound_returns404() throws Exception {
        HttpResponse<String> response = getMovieById(999);

        assertStatusCode(response, STATUS_NOT_FOUND, "Должен вернуть 404 при несуществующем ID");
        assertErrorResponse(response);
    }

    @Test
    void getMovieById_withNonNumericId_returns400() throws Exception {
        HttpRequest request = createGetRequest(MOVIES_ENDPOINT + "/abc");
        HttpResponse<String> response = sendRequest(request);

        assertStatusCode(response, STATUS_BAD_REQUEST, "Должен вернуть 400 при нечисловом ID");
        assertErrorResponse(response);
    }

    @Test
    void deleteMovie_whenExists_returns204() throws Exception {
        HttpResponse<String> createResponse = createMovie(MOVIE_TITLE_1, MOVIE_YEAR_1);
        assertStatusCode(createResponse, STATUS_CREATED, "Должен вернуть 201 при создании фильма");

        Movie createdMovie = GSON.fromJson(createResponse.body(), Movie.class);
        long movieId = createdMovie.getId();

        HttpResponse<String> deleteResponse = deleteMovie(movieId);

        assertStatusCode(deleteResponse, STATUS_NO_CONTENT, "Должен вернуть 204 при успешном удалении");

        HttpResponse<String> getResponse = getMovieById(movieId);
        assertStatusCode(getResponse, STATUS_NOT_FOUND, "Фильм должен быть удален");
    }

    @Test
    void deleteMovie_whenNotFound_returns404() throws Exception {
        HttpResponse<String> response = deleteMovie(999);

        assertStatusCode(response, STATUS_NOT_FOUND, "Должен вернуть 404 при несуществующем ID");
        assertErrorResponse(response);
    }

    @Test
    void deleteMovie_withNonNumericId_returns400() throws Exception {
        HttpRequest request = createDeleteRequest(MOVIES_ENDPOINT + "/abc");
        HttpResponse<String> response = sendRequest(request);

        assertStatusCode(response, STATUS_BAD_REQUEST, "Должен вернуть 400 при нечисловом ID");
        assertErrorResponse(response);
    }

    @Test
    void getMoviesByYear_whenMoviesExist_returnsFilteredList() throws Exception {
        createMovie(MOVIE_TITLE_1, MOVIE_YEAR_1);
        createMovie(MOVIE_TITLE_2, MOVIE_YEAR_2);
        createMovie(MOVIE_TITLE_3, MOVIE_YEAR_3);

        HttpResponse<String> response = getMoviesByYear(MOVIE_YEAR_1);

        assertStatusCode(response, STATUS_OK, "Должен вернуть 200");

        String body = response.body();
        ListOfMoviesTypeToken typeToken = new ListOfMoviesTypeToken();
        List<Movie> movies = GSON.fromJson(body, typeToken.getType());

        assertEquals(1, movies.size(), "Должен вернуть 1 фильм");
        assertEquals(MOVIE_TITLE_1, movies.get(0).getTitle(), "Должен вернуть " + MOVIE_TITLE_1);
        assertEquals(MOVIE_YEAR_1, movies.get(0).getYear(), "Год должен быть " + MOVIE_YEAR_1);
    }

    @Test
    void getMoviesByYear_whenNoMatches_returnsEmptyArray() throws Exception {
        createMovie(MOVIE_TITLE_1, MOVIE_YEAR_1);

        HttpResponse<String> response = getMoviesByYear(2020);

        assertStatusCode(response, STATUS_OK, "Должен вернуть 200");

        String body = response.body().trim();
        assertEquals("[]", body, "Должен вернуть пустой массив");
    }

    @Test
    void getMoviesByYear_withInvalidYear_returns400() throws Exception {
        HttpRequest request = createGetRequest(MOVIES_ENDPOINT + "?year=abc");
        HttpResponse<String> response = sendRequest(request);

        assertStatusCode(response, STATUS_BAD_REQUEST, "Должен вернуть 400 при нечисловом году");
        assertErrorResponse(response);
    }

    @Test
    void unsupportedMethod_returns405() throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(MOVIES_ENDPOINT)).method("PATCH", HttpRequest.BodyPublishers.noBody()).build();

        HttpResponse<String> response = sendRequest(request);

        assertStatusCode(response, STATUS_METHOD_NOT_ALLOWED, "Должен вернуть 405 при неподдерживаемом методе");
        assertErrorResponse(response);
    }
}