package com.matjom.matjom.visit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.visit.entity.VisitEvent;
import com.matjom.matjom.visit.entity.VisitEventType;
import com.matjom.matjom.visit.entity.VisitState;
import com.matjom.matjom.visit.repository.VisitEventRepository;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisitEventService {

    private final VisitEventRepository visitEventRepository;
    private final ObjectMapper objectMapper;

    public VisitEventService(VisitEventRepository visitEventRepository,
                             ObjectMapper objectMapper) {
        this.visitEventRepository = Objects.requireNonNull(visitEventRepository, "visitEventRepository");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Transactional
    public VisitEvent recordEvent(Visit visit,
                                  VisitState fromState,
                                  VisitState toState,
                                  VisitEventType eventType,
                                  OffsetDateTime occurredAt,
                                  ObjectNode meta) {
        Objects.requireNonNull(visit, "visit");
        Objects.requireNonNull(fromState, "fromState");
        Objects.requireNonNull(toState, "toState");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");

        VisitEvent event = new VisitEvent(visit, eventType, fromState, toState, occurredAt, meta);
        return visitEventRepository.save(event);
    }

    public ObjectNode createMetaNode() {
        return objectMapper.createObjectNode();
    }

    public JsonNode deepCopy(JsonNode node) {
        if (node == null) {
            return null;
        }
        return node.deepCopy();
    }
}
