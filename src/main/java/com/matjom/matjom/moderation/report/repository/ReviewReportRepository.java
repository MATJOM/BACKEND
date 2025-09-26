package com.matjom.matjom.moderation.report.repository;

import com.matjom.matjom.moderation.report.entity.ReviewReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewReportRepository extends JpaRepository<ReviewReport, UUID> {
    boolean existsByReviewIdAndReporterId(UUID reviewId, UUID reporterId);
    long countByReviewId(UUID reviewId);
}