package com.matjom.matjom.moderation.report.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.matjom.matjom.moderation.report.entity.ReportReason;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportReviewResponseDTO {
    private final UUID reviewId;
    private final UUID reportId;
    private final String reporterName;
    private final ReportReason reason;
    private final String description;
    private final OffsetDateTime reportedAt;
    private final long reportCount;
}
