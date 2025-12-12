package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MoviesStore {
    private final List<Movie> movies = new ArrayList<>();
    private long nextId = 1;

    public List<Movie> getAllMovies() {
        return new ArrayList<>(movies);
    }

    public Movie addMovie(Movie movie) {
        movie.setId(nextId++);
        movies.add(movie);
        return movie;
    }

    public Movie getMovieById(long id) {
        return movies.stream()
                .filter(movie -> movie.getId() == id)
                .findFirst()
                .orElse(null);
    }

    public boolean deleteMovie(long id) {
        return movies.removeIf(movie -> movie.getId() == id);
    }

    public List<Movie> getMoviesByYear(int year) {
        return movies.stream()
                .filter(movie -> movie.getYear() == year)
                .collect(Collectors.toList());
    }
}