package com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.categories;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategorySummaryResponseDTO {

    private UUID id;

    private String name;
}
