package com.peter_gerdzhikov.url_shortener_backend.services.implementations;

import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.url_shortener_backend.services.interfaces.ShortCodeGenerator;
import com.peter_gerdzhikov.url_shortener_backend.utilities.Base62Encoder;

import cn.hutool.core.lang.Snowflake;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SnowflakeShortCodeGenerator implements ShortCodeGenerator {

    private final Snowflake snowflake;

    @Override
    public String generate() {
        return Base62Encoder.encode(snowflake.nextId());
    }
}
