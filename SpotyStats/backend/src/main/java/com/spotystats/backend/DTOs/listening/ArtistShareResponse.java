package com.spotystats.backend.dtos.listening;

import lombok.Value;


/**
 * One artist's slice of the listening donut, attributed by primary artist.
 */
@Value
public class ArtistShareResponse {

    String artistName;

    long trackCount;

    long listeningTimeMs;
}
