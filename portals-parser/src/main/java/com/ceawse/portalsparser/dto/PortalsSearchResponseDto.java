package com.ceawse.portalsparser.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PortalsSearchResponseDto {
    private List<PortalsNftDto> results;
}