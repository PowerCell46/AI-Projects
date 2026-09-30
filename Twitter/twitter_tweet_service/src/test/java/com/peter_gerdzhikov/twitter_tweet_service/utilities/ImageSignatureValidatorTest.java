package com.peter_gerdzhikov.twitter_tweet_service.utilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.peter_gerdzhikov.twitter_tweet_service.exceptions.images.UnsupportedImageTypeException;

class ImageSignatureValidatorTest {

    private static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF);

    private static final byte[] PNG = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);

    private static final byte[] WEBP = ascii("RIFF\0\0\0\0WEBP");

    @Nested
    class DetectContentType {

        @Test
        void jpeg_signature_returns_image_jpeg() {
            assertThat(ImageSignatureValidator.detectContentType(JPEG)).isEqualTo("image/jpeg");
        }

        @Test
        void jpeg_signature_followed_by_more_bytes_returns_image_jpeg() {
            assertThat(ImageSignatureValidator.detectContentType(withTail(JPEG, 9))).isEqualTo("image/jpeg");
        }

        @Test
        void png_signature_returns_image_png() {
            assertThat(ImageSignatureValidator.detectContentType(withTail(PNG, 4))).isEqualTo("image/png");
        }

        @Test
        void exactly_eight_png_bytes_return_image_png() {
            assertThat(ImageSignatureValidator.detectContentType(PNG)).isEqualTo("image/png");
        }

        @Test
        void webp_signature_returns_image_webp() {
            assertThat(ImageSignatureValidator.detectContentType(WEBP)).isEqualTo("image/webp");
        }

        @Test
        void webp_size_field_of_any_value_returns_image_webp() {
            byte[] header = ascii("RIFF\0\0\0\0WEBP");
            System.arraycopy(bytes(0xFF, 0xFF, 0xFF, 0x7F), 0, header, 4, 4);

            assertThat(ImageSignatureValidator.detectContentType(header)).isEqualTo("image/webp");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.peter_gerdzhikov.twitter_tweet_service.utilities.ImageSignatureValidatorTest#unsupportedHeaders")
        void unsupported_header_throws(String description, byte[] header) {
            assertThrows(UnsupportedImageTypeException.class, () -> ImageSignatureValidator.detectContentType(header));
        }
    }

    static Stream<Arguments> unsupportedHeaders() {
        return Stream.of(
                Arguments.of("empty", new byte[0]),
                Arguments.of("gif89a", ascii("GIF89a\0\0\0\0\0\0")),
                Arguments.of("gif87a", ascii("GIF87a\0\0\0\0\0\0")),
                Arguments.of("plain text", ascii("hello world!")),
                Arguments.of("html", ascii("<html><body>")),
                Arguments.of("svg", ascii("<svg xmlns=\"")),
                Arguments.of("jpeg missing the last signature byte", bytes(0xFF, 0xD8)),
                Arguments.of("jpeg with a wrong third byte", bytes(0xFF, 0xD8, 0x00, 0x00)),
                Arguments.of("png missing the last signature byte", Arrays.copyOf(PNG, PNG.length - 1)),
                Arguments.of("png with a wrong last signature byte", withLastByte(PNG, 0x0B)),
                Arguments.of("png with a lf-crlf mangled signature", bytes(0x89, 0x50, 0x4E, 0x47, 0x0A, 0x0D, 0x1A, 0x0A)),
                Arguments.of("riff without room for the webp tag", ascii("RIFF\0\0\0\0WEB")),
                Arguments.of("riff wave audio", ascii("RIFF\0\0\0\0WAVE")),
                Arguments.of("riff avi video", ascii("RIFF\0\0\0\0AVI ")),
                Arguments.of("webp tag without the riff prefix", ascii("XXXX\0\0\0\0WEBP")),
                Arguments.of("webp tag at the wrong offset", ascii("RIFFWEBP\0\0\0\0")),
                Arguments.of("lowercase riff", ascii("riff\0\0\0\0WEBP")),
                Arguments.of("signature not at the start", withPrefix(0x00, JPEG)),
                Arguments.of("windows executable", ascii("MZ\0\0\0\0\0\0\0\0\0\0")),
                Arguments.of("pdf", ascii("%PDF-1.7\n%\0\0\0"))
        );
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }

        return result;
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] withTail(byte[] signature, int extraBytes) {
        return Arrays.copyOf(signature, signature.length + extraBytes);
    }

    private static byte[] withLastByte(byte[] signature, int last) {
        byte[] copy = signature.clone();
        copy[copy.length - 1] = (byte) last;
        return copy;
    }

    private static byte[] withPrefix(int prefix, byte[] signature) {
        byte[] result = new byte[signature.length + 1];
        result[0] = (byte) prefix;
        System.arraycopy(signature, 0, result, 1, signature.length);
        return result;
    }
}
