package com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.categories;

public class CategoryInUseException extends RuntimeException {

    public static final String MESSAGE = "This category is referenced by an interest topic and cannot be deleted.";

    public CategoryInUseException() {
        super(MESSAGE);
    }
}
