package com.peter_gerdzhikov.twitter_tweet_service.DTOs.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

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

    /**
     * Only the answers a client has to tell apart from others of the same status carry one.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String code;

    public ErrorResponseDTO(int status, List<String> messages, long timestamp) {
        this(status, messages, timestamp, null);
    }
}
