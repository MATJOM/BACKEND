package com.matjom.matjom.feed.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReviewCreatedEvent(
        UUID reviewId,
        UUID userId,
        Long placeId,
        OffsetDateTime createdAt
) {}
