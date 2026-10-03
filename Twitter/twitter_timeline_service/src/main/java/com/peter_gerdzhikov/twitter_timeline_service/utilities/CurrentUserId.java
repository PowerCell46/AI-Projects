package com.peter_gerdzhikov.twitter_timeline_service.utilities;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the caller's id from the {@code X-User-Id} header the gateway sets. Only valid on a
 * {@code UUID} parameter.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {
}
