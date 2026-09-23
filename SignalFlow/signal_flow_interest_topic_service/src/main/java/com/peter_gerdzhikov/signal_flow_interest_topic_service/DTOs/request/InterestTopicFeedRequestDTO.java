package com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.request;

import java.util.List;
import java.util.UUID;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopicFeedMode;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code after} is the name of the last topic already shown - a keyset cursor, so a page never shifts
 * when topics before it drop out of the view. Null starts from the beginning.
 */
@Data
@NoArgsConstructor
public class InterestTopicFeedRequestDTO {

    @NotNull
    private List<@NotNull UUID> ids;

    @NotNull
    private InterestTopicFeedMode mode;

    @Size(max = 100)
    private String after;

    @Min(1)
    @Max(100)
    private int size;
}
