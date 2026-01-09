package com.ceawse.portalsparser.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Data
@Builder
@Document(collection = "unique_gifts")
public class UniqueGiftDocument {
    @Id
    private String id;

    @Indexed
    private String name;

    @Indexed
    private String collectionAddress;

    private GiftAttributes attributes;

    private Instant lastSeenAt;

    @Data
    @Builder
    public static class GiftAttributes {
        private String model;
        private String backdrop;
        private String symbol;
        private Instant updatedAt;
    }
}