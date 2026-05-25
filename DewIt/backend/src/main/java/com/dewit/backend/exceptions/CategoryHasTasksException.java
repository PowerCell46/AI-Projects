package com.dewit.backend.exceptions;

public class CategoryHasTasksException extends RuntimeException {

    public CategoryHasTasksException(String message) {
        super(message);
    }
}
