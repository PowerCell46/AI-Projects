package com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponseDTO {

    private int status;

    private List<String> messages;

    private long timestamp;
}
