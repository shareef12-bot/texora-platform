package com.texora.secops.siem.correlation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.siem.correlation.model.RuleDefinition;
import com.texora.secops.siem.correlation.model.RuleDefinitionException;
import org.springframework.stereotype.Component;

@Component
public class RuleDefinitionParser {

    private final ObjectMapper objectMapper;

    public RuleDefinitionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RuleDefinition parse(String json) {
        if (json == null || json.isBlank()) {
            throw new RuleDefinitionException("Rule definition is empty");
        }
        RuleDefinition definition;
        try {
            definition = objectMapper.readValue(json, RuleDefinition.class);
        } catch (Exception ex) {
            throw new RuleDefinitionException("Rule definition is not valid JSON: " + ex.getMessage(), ex);
        }
        if (definition.getConditions() == null || definition.getConditions().isEmpty()) {
            throw new RuleDefinitionException("Rule definition must declare at least one condition");
        }
        if (definition.getConditions().size() > 2) {
            throw new RuleDefinitionException(
                    "This engine supports at most a two-stage condition chain per rule");
        }
        if (definition.getSeverity() == null) {
            throw new RuleDefinitionException("Rule definition must declare a severity");
        }
        return definition;
    }
}
