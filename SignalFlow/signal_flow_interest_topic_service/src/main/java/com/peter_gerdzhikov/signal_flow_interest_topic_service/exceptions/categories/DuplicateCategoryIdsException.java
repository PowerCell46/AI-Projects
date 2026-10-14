package com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.categories;

public class DuplicateCategoryIdsException extends RuntimeException {

    public static final String MESSAGE = "Category ids must be unique.";

    public DuplicateCategoryIdsException() {
        super(MESSAGE);
    }
}
