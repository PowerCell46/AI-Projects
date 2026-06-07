package com.spotystats.backend.dtos.listening;

import lombok.Value;

import java.time.LocalDate;
import java.util.List;


/**
 * A page of the listening diary: up to a week of days (newest first) and a
 * cursor for the next page. {@code nextBefore} is the exclusive upper date
 * bound to request next — already positioned past any play-free gap — or null
 * when nothing older exists.
 */
@Value
public class HistoryPageResponse {

    List<DailyHistoryResponse> days;

    LocalDate nextBefore;
}
