package com.spotystats.backend.dtos.listening;

import lombok.Value;

import java.time.LocalDate;
import java.util.List;


/**
 * One calendar day's plays (in the caller's time zone), newest first.
 */
@Value
public class DailyHistoryResponse {

    LocalDate date;

    List<PlayedTrackResponse> tracks;
}
