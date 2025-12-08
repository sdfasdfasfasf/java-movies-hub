package ru.practicum.moviehub.validation;

import ru.practicum.moviehub.model.Movie;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;

public class MovieValidator {

    public static List<String> validate(Movie movie) {
        List<String> errors = new ArrayList<>();

        if (movie.getTitle() == null || movie.getTitle().trim().isEmpty()) {
            errors.add("название не должно быть пустым");
        } else if (movie.getTitle().length() > 100) {
            errors.add("название не должно превышать 100 символов");
        }

        int currentYear = Year.now().getValue();
        if (movie.getYear() < 1888) {
            errors.add("год должен быть не менее 1888");
        } else if (movie.getYear() > currentYear + 1) {
            errors.add("год должен быть не более " + (currentYear + 1));
        }

        return errors;
    }
}