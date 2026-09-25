package com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.categories;

public class CategoryLimitExceededException extends RuntimeException {

    public static final String MESSAGE = "The maximum number of categories has been reached.";

    public CategoryLimitExceededException() {
        super(MESSAGE);
    }
}
