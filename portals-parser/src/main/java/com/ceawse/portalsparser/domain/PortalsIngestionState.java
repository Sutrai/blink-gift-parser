package com.ceawse.portalsparser.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "portals_ingestion_state")
public class PortalsIngestionState {
    @Id
    private String id;
    private Long lastProcessedTimestamp;
}