package com.ceawse.portalsparser.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PortalsActionsResponseDto {
    private List<ActionDto> actions;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ActionDto {
        @JsonProperty("offer_id")
        private String offerId; // Используем как уникальный ID события или хеш

        private String type; // buy, listing, price_update

        private String amount; // цена

        @JsonProperty("created_at")
        private String createdAt; // ISO String

        private PortalsNftDto nft;
    }
}