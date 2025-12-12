package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpServer;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MoviesServer {
    private final HttpServer server;
    private final MoviesStore store;
    private final int port;

    public MoviesServer(MoviesStore store, int port) {
        this.store = store;
        this.port = port;
        try {
            this.server = HttpServer.create(new InetSocketAddress(port), 0);
            configureRoutes();
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать HTTP-сервер", e);
        }
    }

    private void configureRoutes() {
        // Регистрируем обработчики
        server.createContext("/movies", new MoviesHandler(store));
        server.createContext("/movies/", new MovieByIdHandler(store));
    }

    public void start() {
        server.start();
        System.out.println("Сервер запущен на порту " + port);
    }

    public void stop() {
        server.stop(0);
        System.out.println("Сервер остановлен");
    }
}