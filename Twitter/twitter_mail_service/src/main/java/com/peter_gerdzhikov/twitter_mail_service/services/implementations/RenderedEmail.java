package com.peter_gerdzhikov.twitter_mail_service.services.implementations;

import lombok.Value;

@Value
public class RenderedEmail {

    private final String html;

    private final String text;
}
