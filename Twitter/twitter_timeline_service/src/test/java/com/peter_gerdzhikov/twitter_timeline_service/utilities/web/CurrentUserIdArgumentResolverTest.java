package com.peter_gerdzhikov.twitter_timeline_service.utilities.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import com.peter_gerdzhikov.twitter_timeline_service.exceptions.InvalidCallerIdentityException;

class CurrentUserIdArgumentResolverTest {

    private final CurrentUserIdArgumentResolver resolver = new CurrentUserIdArgumentResolver();

    @Nested
    class SupportsParameter {

        @Test
        void should_support_a_uuid_parameter_annotated_with_current_user_id() throws Exception {
            assertThat(resolver.supportsParameter(parameter("annotatedUuid", 0))).isTrue();
        }

        @Test
        void should_not_support_a_uuid_parameter_without_the_annotation() throws Exception {
            assertThat(resolver.supportsParameter(parameter("plainUuid", 0))).isFalse();
        }

        @Test
        void should_not_support_an_annotated_parameter_that_is_not_a_uuid() throws Exception {
            assertThat(resolver.supportsParameter(parameter("annotatedString", 0))).isFalse();
        }
    }

    @Nested
    class ResolveArgument {

        @Test
        void should_return_the_uuid_when_the_header_is_a_canonical_uuid() throws Exception {
            UUID userId = UUID.randomUUID();

            Object resolved = resolve(userId.toString());

            assertThat(resolved).isEqualTo(userId);
        }

        @Test
        void should_accept_an_uppercase_uuid() throws Exception {
            UUID userId = UUID.randomUUID();

            Object resolved = resolve(userId.toString().toUpperCase());

            assertThat(resolved).isEqualTo(userId);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "not-a-uuid", "1-1-1-1-1", "123e4567-e89b-12d3-a456-42661417400", " 123e4567-e89b-12d3-a456-426614174000"})
        void should_throw_when_the_header_is_missing_or_not_a_canonical_uuid(String header) {
            assertThatThrownBy(() -> resolve(header)).isInstanceOf(InvalidCallerIdentityException.class);
        }
    }

    private Object resolve(String header) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (header != null) {
            request.addHeader(CurrentUserIdArgumentResolver.USER_ID_HEADER, header);
        }

        return resolver.resolveArgument(parameter("annotatedUuid", 0), null, new ServletWebRequest(request), null);
    }

    private MethodParameter parameter(String methodName, int index) throws Exception {
        for (Method method : Probe.class.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return new MethodParameter(method, index);
            }
        }

        throw new IllegalArgumentException(methodName);
    }

    private static final class Probe {

        void annotatedUuid(@CurrentUserId UUID userId) {
        }

        void plainUuid(UUID userId) {
        }

        void annotatedString(@CurrentUserId String userId) {
        }
    }
}
