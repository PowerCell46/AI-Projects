package com.peter_gerdzhikov.twitter_api_gateway.utilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.InvalidCursorException;

class FollowCursorCodecTest {

    private static final UUID ID = UUID.fromString("0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00.123456Z");

    @Nested
    class Encode {

        @Test
        void should_produce_unpadded_base64url_of_epoch_micros_and_id() {
            String cursor = FollowCursorCodec.encode(CREATED_AT, ID);

            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            assertThat(raw).isEqualTo("1767225600123456:" + ID);
            assertThat(cursor).doesNotContain("=", "+", "/");
        }
    }

    @Nested
    class Decode {

        @Test
        void should_return_the_same_position_when_the_cursor_was_encoded() {
            FollowCursor decoded = FollowCursorCodec.decode(FollowCursorCodec.encode(CREATED_AT, ID));

            assertThat(decoded.getCreatedAt()).isEqualTo(CREATED_AT);
            assertThat(decoded.getId()).isEqualTo(ID);
        }

        @Test
        void should_keep_microsecond_precision_when_the_instant_is_a_single_microsecond_past_a_second() {
            Instant instant = Instant.parse("2026-01-01T00:00:01.000001Z");

            assertThat(FollowCursorCodec.decode(FollowCursorCodec.encode(instant, ID)).getCreatedAt()).isEqualTo(instant);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "not-a-cursor!", "====", "aGVsbG8"})
        void should_reject_input_that_is_not_a_cursor(String cursor) {
            assertThatThrownBy(() -> FollowCursorCodec.decode(cursor)).isInstanceOf(InvalidCursorException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "1767225600123456",
                "1767225600123456:",
                ":0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b",
                "abc:0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b",
                "-5:0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b",
                "+5:0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b",
                "05:0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b",
                "9999999999999999999:0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b",
                "5:not-a-uuid",
                "5:1-1-1-1-1",
                "5:0190A1B2-C3D4-7E5F-8A9B-0C1D2E3F4A5B",
                "5:0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b:extra"
        })
        void should_reject_well_formed_base64url_when_the_content_is_not_micros_colon_id(String raw) {
            String cursor = Base64
                    .getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() -> FollowCursorCodec.decode(cursor)).isInstanceOf(InvalidCursorException.class);
        }

        @Test
        void should_reject_a_padded_cursor_when_the_encoded_form_has_none() {
            String padded = Base64
                    .getUrlEncoder()
                    .encodeToString(("5:" + ID).getBytes(StandardCharsets.UTF_8));

            assertThat(padded).endsWith("=");
            assertThatThrownBy(() -> FollowCursorCodec.decode(padded)).isInstanceOf(InvalidCursorException.class);
        }
    }
}
