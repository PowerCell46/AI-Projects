package com.peter_gerdzhikov.url_shortener_backend.configurations;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.peter_gerdzhikov.url_shortener_backend.interceptors.ScannerProbeInterceptor;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfiguration implements WebMvcConfigurer {

    private final ScannerProbeInterceptor scannerProbeInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(scannerProbeInterceptor)
                .addPathPatterns("/*");
    }
}
