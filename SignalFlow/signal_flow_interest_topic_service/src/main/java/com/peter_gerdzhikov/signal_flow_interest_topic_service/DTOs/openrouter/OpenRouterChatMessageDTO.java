package com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.openrouter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenRouterChatMessageDTO {

    private String role;

    private String content;
}
