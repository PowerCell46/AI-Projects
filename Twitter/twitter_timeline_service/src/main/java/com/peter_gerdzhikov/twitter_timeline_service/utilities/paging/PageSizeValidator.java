package com.peter_gerdzhikov.twitter_timeline_service.utilities.paging;

import com.peter_gerdzhikov.twitter_timeline_service.exceptions.paging.InvalidPageSizeException;

/**
 * The page size every list here accepts: 1 to 100.
 */
public final class PageSizeValidator {

    public static final int MIN_PAGE_SIZE = 1;

    public static final int MAX_PAGE_SIZE = 100;

    private PageSizeValidator() {
    }

    public static void validate(int size) {
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new InvalidPageSizeException(MIN_PAGE_SIZE, MAX_PAGE_SIZE);
        }
    }
}
