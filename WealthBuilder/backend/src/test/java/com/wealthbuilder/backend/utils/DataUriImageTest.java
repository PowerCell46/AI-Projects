    package com.wealthbuilder.backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/**
 * Pure unit test for the data-URI parser. No Spring context: {@link DataUriImage#parse} is
 * exercised directly against hand-built URIs.
 */
class DataUriImageTest {

    private static final byte[] PAYLOAD = "hello-image".getBytes(StandardCharsets.UTF_8);

    private static final String BASE64_PAYLOAD = Base64.getEncoder().encodeToString(PAYLOAD);

    @Nested
    @DisplayName("Valid data URIs")
    class Valid {

        @Test
        void should_ParsePngMediaTypeAndBytes_When_UriIsWellFormed() {
            final DataUriImage image = DataUriImage.parse("data:image/png;base64," + BASE64_PAYLOAD);

            assertThat(image.getMediaType()).isEqualTo(MediaType.IMAGE_PNG);
            assertThat(image.getBytes()).isEqualTo(PAYLOAD);
        }

        @Test
        void should_ParseJpegMediaType_When_UriIsJpeg() {
            final DataUriImage image = DataUriImage.parse("data:image/jpeg;base64," + BASE64_PAYLOAD);

            assertThat(image.getMediaType()).isEqualTo(MediaType.IMAGE_JPEG);
            assertThat(image.getBytes()).isEqualTo(PAYLOAD);
        }

        @Test
        void should_DecodePayload_When_Base64ContainsWhitespaceAndNewlines() {
            final String chunked = BASE64_PAYLOAD.substring(0, 4) + "\n  " + BASE64_PAYLOAD.substring(4);

            final DataUriImage image = DataUriImage.parse("data:image/png;base64," + chunked);

            assertThat(image.getBytes()).isEqualTo(PAYLOAD);
        }

        @Test
        void should_KeepVendorMediaType_When_SubtypeHasPlusSuffix() {
            final DataUriImage image = DataUriImage.parse("data:image/svg+xml;base64," + BASE64_PAYLOAD);

            assertThat(image.getMediaType()).isEqualTo(MediaType.parseMediaType("image/svg+xml"));
        }
    }

    @Nested
    @DisplayName("Malformed input")
    class Malformed {

        @Test
        void should_Throw_When_PrefixIsNotData() {
            assertThatThrownBy(() -> DataUriImage.parse("image/png;base64," + BASE64_PAYLOAD))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not a data URI");
        }

        @Test
        void should_Throw_When_NoCommaSeparator() {
            assertThatThrownBy(() -> DataUriImage.parse("data:image/png;base64" + BASE64_PAYLOAD))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not a data URI");
        }

        @Test
        void should_Throw_When_PayloadIsNotValidBase64() {
            assertThatThrownBy(() -> DataUriImage.parse("data:image/png;base64,@@@not-base64@@@"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
