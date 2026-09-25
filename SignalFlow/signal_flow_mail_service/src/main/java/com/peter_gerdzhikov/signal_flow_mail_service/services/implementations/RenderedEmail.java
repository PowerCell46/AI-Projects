package com.peter_gerdzhikov.signal_flow_mail_service.services.implementations;

import lombok.Value;

@Value
public class RenderedEmail {

    String html;

    String text;
}
