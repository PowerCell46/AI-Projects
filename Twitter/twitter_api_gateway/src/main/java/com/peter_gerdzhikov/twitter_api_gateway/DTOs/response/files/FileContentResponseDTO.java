package com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.files;

import java.io.InputStream;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * A stored file ready to stream. The holder of {@code content} must close it.
 */
@Getter
@Builder
@AllArgsConstructor
public class FileContentResponseDTO {

    private final long sizeBytes;

    private final String contentType;

    private final InputStream content;
}
