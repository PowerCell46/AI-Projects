package com.spotystats.backend.DTOs.listening;

import lombok.Value;

import java.time.LocalDate;
import java.util.List;


/**
 * The current calendar day's plays (in the caller's time zone), newest first.
 */
@Value
public class TodayHistoryResponse {

    LocalDate date;

    List<PlayedTrackResponse> tracks;
}
