package com.peter_gerdzhikov.twitter_api_gateway.DTOs.request;

import java.time.LocalDate;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.validation.MaxCodePoints;

import jakarta.validation.constraints.Past;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequestDTO {

    @MaxCodePoints(160)
    private String bio;

    @MaxCodePoints(60)
    private String location;

    @Past
    private LocalDate birthdate;
}
