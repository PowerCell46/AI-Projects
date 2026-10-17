package com.peter_gerdzhikov.twitter_mail_service.utilities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EmailTextTest {

    @ParameterizedTest
    @MethodSource("valuesWithTheirLineBreaksStripped")
    void stripLineBreaks_removesEveryCarriageReturnAndLineFeed(String input, String expected) {
        assertEquals(expected, EmailText.stripLineBreaks(input));
    }

    private static Stream<Arguments> valuesWithTheirLineBreaksStripped() {
        return Stream.of(
                Arguments.of("plain text", "plain text"),
                Arguments.of("a\nb", "ab"),
                Arguments.of("a\rb", "ab"),
                Arguments.of("a\r\nb", "ab"),
                Arguments.of("Hi\r\nBcc: evil@example.com", "HiBcc: evil@example.com"),
                Arguments.of("\n\r", "")
        );
    }
}
