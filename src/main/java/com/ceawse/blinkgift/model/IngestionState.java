package com.blinkgift.parser.getgems.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "parser_ingestion_state")
public class IngestionState {

    @Id
    private String id; // Например: "GETGEMS_BACKFILL" или "GETGEMS_REALTIME"

    private Long lastProcessedTimestamp; // Время последнего успешного события
    private String lastCursor;           // Курсор API (если нужен)
    private String status;               // RUNNING, PAUSED, FINISHED
}