package com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.categories;

public class DuplicateCategoryNameException extends RuntimeException {

    public static final String MESSAGE = "This category name is already in use.";

    public DuplicateCategoryNameException() {
        super(MESSAGE);
    }
}
