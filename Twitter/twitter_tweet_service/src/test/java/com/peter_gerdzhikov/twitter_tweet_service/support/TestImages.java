package com.peter_gerdzhikov.twitter_tweet_service.support;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import javax.imageio.ImageIO;

/**
 * Byte arrays the tests upload. The JPEG and PNG are real 1x1 images written by {@code ImageIO}; the WebP is
 * a fixed 1x1 lossless file, because the JDK cannot write WebP. Every method returns a fresh array.
 */
public final class TestImages {

    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;

    private static final String WEBP_1X1_BASE64 = "UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==";

    private TestImages() {
    }

    public static byte[] jpeg() {
        return write("jpg");
    }

    public static byte[] png() {
        return write("png");
    }

    public static byte[] webp() {
        return Base64.getDecoder().decode(WEBP_1X1_BASE64);
    }

    /** The bytes carry no image signature; giving them an image-looking filename is the caller's job. */
    public static byte[] notAnImage() {
        return "definitely not an image".getBytes(StandardCharsets.UTF_8);
    }

    /** A valid JPEG signature followed by padding, one byte over the per-file limit. */
    public static byte[] oversizedJpeg() {
        byte[] bytes = new byte[MAX_FILE_BYTES + 1];
        byte[] signature = jpeg();
        System.arraycopy(signature, 0, bytes, 0, Math.min(signature.length, bytes.length));
        Arrays.fill(bytes, signature.length, bytes.length, (byte) 0);

        return bytes;
    }

    private static byte[] write(String format) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), format, out);

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return out.toByteArray();
    }
}
