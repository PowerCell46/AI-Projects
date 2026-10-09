package com.peter_gerdzhikov.twitter_timeline_service.utilities.web;

import java.util.UUID;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.peter_gerdzhikov.twitter_timeline_service.exceptions.InvalidCallerIdentityException;

@Component
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String USER_ID_HEADER = "X-User-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && UUID.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        String header = webRequest.getHeader(USER_ID_HEADER);
        if (header == null) {
            throw new InvalidCallerIdentityException();
        }

        return parseCanonicalUuid(header);
    }

    /**
     * {@code UUID.fromString} accepts shortened forms such as {@code 1-1-1-1-1}, so the parsed value is
     * compared back against the input to accept only the canonical 36-character form.
     */
    private UUID parseCanonicalUuid(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equalsIgnoreCase(value)) {
                throw new InvalidCallerIdentityException();
            }

            return parsed;

        } catch (IllegalArgumentException e) {
            throw new InvalidCallerIdentityException();
        }
    }
}
