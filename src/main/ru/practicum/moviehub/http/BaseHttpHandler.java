package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import ru.practicum.moviehub.api.ErrorResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public abstract class BaseHttpHandler implements HttpHandler {
    protected static final Gson GSON = new GsonBuilder().create();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            switch (exchange.getRequestMethod()) {
                case "GET":
                    handleGet(exchange);
                    break;
                case "POST":
                    handlePost(exchange);
                    break;
                case "DELETE":
                    handleDelete(exchange);
                    break;
                default:
                    ErrorResponse error = new ErrorResponse("Метод не поддерживается");
                    sendResponse(exchange, 405, GSON.toJson(error));
            }
        } catch (Exception e) {
            e.printStackTrace();
            ErrorResponse error = new ErrorResponse("Внутренняя ошибка сервера");
            sendResponse(exchange, 500, GSON.toJson(error));
        }
    }

    protected abstract void handleGet(HttpExchange exchange) throws IOException;

    protected void handlePost(HttpExchange exchange) throws IOException {
        ErrorResponse error = new ErrorResponse("Метод не поддерживается");
        sendResponse(exchange, 405, GSON.toJson(error));
    }

    protected void handleDelete(HttpExchange exchange) throws IOException {
        ErrorResponse error = new ErrorResponse("Метод не поддерживается");
        sendResponse(exchange, 405, GSON.toJson(error));
    }

    protected void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    protected void sendResponse(HttpExchange exchange, int statusCode) throws IOException {
        exchange.sendResponseHeaders(statusCode, -1);
    }
}