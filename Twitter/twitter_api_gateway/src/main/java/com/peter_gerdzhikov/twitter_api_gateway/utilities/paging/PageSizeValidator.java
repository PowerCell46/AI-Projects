package com.peter_gerdzhikov.twitter_api_gateway.utilities.paging;

import com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.InvalidPageSizeException;

public final class PageSizeValidator {

    public static final int MIN_PAGE_SIZE = 1;

    public static final int MAX_PAGE_SIZE = 100;

    private PageSizeValidator() {
    }

    public static void validate(int size) {
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new InvalidPageSizeException();
        }
    }
}
