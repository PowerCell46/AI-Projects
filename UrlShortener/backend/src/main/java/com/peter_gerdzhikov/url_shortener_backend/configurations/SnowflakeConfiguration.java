package com.peter_gerdzhikov.url_shortener_backend.configurations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;

@Configuration
public class SnowflakeConfiguration {

    @Bean
    public Snowflake snowflake(
            @Value("${url-shortener.snowflake.worker-id}") long workerId,
            @Value("${url-shortener.snowflake.datacenter-id}") long datacenterId
    ) {
        return IdUtil.getSnowflake(workerId, datacenterId);
    }
}
