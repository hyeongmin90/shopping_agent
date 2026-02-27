package com.shopping.inventory.kafka.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class CommandEnvelope {
    private CommandMeta meta;
    private JsonNode data;
}
