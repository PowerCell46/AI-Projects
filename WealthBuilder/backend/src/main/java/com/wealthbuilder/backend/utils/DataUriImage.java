package com.wealthbuilder.backend.utils;

import lombok.Value;
import org.springframework.http.MediaType;

import java.util.Base64;


/**
 * Decoded view of a {@code data:image/...;base64,...} URI: the media type and the raw image
 * bytes, ready to stream from the image endpoint. Parsing here keeps the controller free of
 * string-splitting and base64 concerns.
 */
@Value
public class DataUriImage {

    MediaType mediaType;

    byte[] bytes;

    /**
     * Splits a data URI into its media type and decoded payload. The stored value is already
     * validated on write, so a malformed URI here signals corrupt data and fails loudly.
     */
    public static DataUriImage parse(String dataUri) {
        final int commaIndex = dataUri.indexOf(',');
        if (!dataUri.startsWith("data:") || commaIndex < 0) {
            throw new IllegalArgumentException("Not a data URI");
        }

        final String mediaTypePart = dataUri
                .substring("data:".length(), commaIndex)
                .replace(";base64", "");

        final byte[] bytes = Base64
                .getDecoder()
                .decode(dataUri.substring(commaIndex + 1).replaceAll("\\s", ""));

        return new DataUriImage(MediaType.parseMediaType(mediaTypePart), bytes);
    }
}
