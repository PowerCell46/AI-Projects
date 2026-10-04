package com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.storage;

import java.io.InputStream;

public interface ObjectStorageService {

    void put(String objectKey, InputStream content, long sizeBytes, String contentType);

    /**
     * The caller owns the returned stream and must close it.
     */
    InputStream get(String objectKey);

    /**
     * Deleting a key that doesn't exist is not an error.
     */
    void delete(String objectKey);
}
