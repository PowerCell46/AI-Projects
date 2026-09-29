package com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.profile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileResponseDTO {

    private UUID id;

    private String bio;

    private String username;

    private String location;

    private Instant createdAt;

    private LocalDate birthdate;

    private String coverPictureUrl;

    private String profilePictureUrl;
}
