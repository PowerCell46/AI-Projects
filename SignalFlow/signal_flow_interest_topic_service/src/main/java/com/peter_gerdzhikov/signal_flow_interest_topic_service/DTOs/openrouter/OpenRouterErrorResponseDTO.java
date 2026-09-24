package com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.openrouter;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Only OpenRouter's failure shape, {@code {"error": {"message": "...", ...}}}. {@code error} stays a
 * {@code Map} rather than its own DTO class since {@code message} is the only key ever read.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenRouterErrorResponseDTO {

    private Map<String, Object> error;
}
