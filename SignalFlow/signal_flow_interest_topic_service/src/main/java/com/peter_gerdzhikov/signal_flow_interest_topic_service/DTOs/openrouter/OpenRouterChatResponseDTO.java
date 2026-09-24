package com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.openrouter;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenRouterChatResponseDTO {

    private List<OpenRouterChoiceDTO> choices;
}
