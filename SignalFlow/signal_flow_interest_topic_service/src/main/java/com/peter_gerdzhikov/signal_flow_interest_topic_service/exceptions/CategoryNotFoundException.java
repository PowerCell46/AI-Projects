package com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions;

public class CategoryNotFoundException extends RuntimeException {

    public static final String MESSAGE = "Category not found.";

    public CategoryNotFoundException() {
        super(MESSAGE);
    }
}
