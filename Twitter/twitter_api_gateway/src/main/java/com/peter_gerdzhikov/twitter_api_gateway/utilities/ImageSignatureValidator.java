package com.peter_gerdzhikov.twitter_api_gateway.utilities;

import java.util.Arrays;

import com.peter_gerdzhikov.twitter_api_gateway.exceptions.files.UnsupportedImageTypeException;

/**
 * Decides the image type from the leading bytes alone. The client's {@code Content-Type} and filename are
 * never consulted, since both are attacker-controlled.
 */
public final class ImageSignatureValidator {

    public static final int HEADER_BYTES = 12;

    private static final int WEBP_OFFSET = 8;

    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private static final byte[] RIFF_SIGNATURE = {'R', 'I', 'F', 'F'};

    private static final byte[] WEBP_SIGNATURE = {'W', 'E', 'B', 'P'};

    private ImageSignatureValidator() {
    }

    public static String detectContentType(byte[] header) {
        if (startsWith(header, 0, JPEG_SIGNATURE)) {
            return "image/jpeg";
        }

        if (startsWith(header, 0, PNG_SIGNATURE)) {
            return "image/png";
        }

        if (startsWith(header, 0, RIFF_SIGNATURE) && startsWith(header, WEBP_OFFSET, WEBP_SIGNATURE)) {
            return "image/webp";
        }

        throw new UnsupportedImageTypeException();
    }

    private static boolean startsWith(byte[] bytes, int offset, byte[] expected) {
        int end = offset + expected.length;

        return bytes.length >= end && Arrays.equals(bytes, offset, end, expected, 0, expected.length);
    }
}
