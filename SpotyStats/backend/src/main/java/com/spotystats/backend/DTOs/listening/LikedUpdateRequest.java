package com.spotystats.backend.dtos.listening;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * Desired liked state for a track — true saves it to the user's Spotify library,
 * false removes it.
 */
@Getter
@Setter
@NoArgsConstructor
public class LikedUpdateRequest {

    @NotNull
    private Boolean liked;
}
