package com.peter_gerdzhikov.twitter_api_gateway.DTOs.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequestDTO {

    @Size(max = 160)
    private String bio;

    @Size(max = 30)
    private String location;

    @Past
    private LocalDate birthdate;
}
