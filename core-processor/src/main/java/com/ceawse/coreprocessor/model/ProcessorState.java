package com.ceawse.coreprocessor.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "processor_state")
public class ProcessorState {
    @Id
    private String id; // "MAIN_PROCESSOR"
    private Long lastProcessedTimestamp; // Время последнего успешного события
}