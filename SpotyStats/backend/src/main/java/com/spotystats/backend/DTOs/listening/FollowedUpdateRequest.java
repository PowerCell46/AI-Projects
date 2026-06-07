package com.spotystats.backend.dtos.listening;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * Desired follow state for an artist — true follows them on the user's Spotify
 * account, false unfollows.
 */
@Getter
@Setter
@NoArgsConstructor
public class FollowedUpdateRequest {

    @NotNull
    private Boolean followed;
}
