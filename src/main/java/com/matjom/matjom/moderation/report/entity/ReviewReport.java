package com.matjom.matjom.moderation.report.entity;

import com.matjom.matjom.common.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * 리뷰 신고 정보를 저장하는 엔티티.
 * 사용 목적: 신고자·리뷰·사유를 영속화해 누적 신고 수를 추적한다.
 * 코드 의미: 신고 상세(사유, 설명)를 JPA 매핑으로 정의하고 BaseEntity를 상속해 공통 필드를 사용한다.
 * 기대 결과: 신고 저장/조회 시 일관된 스키마를 통해 데이터를 다룬다.
 */

@Entity
@Table(name = "review_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ReviewReport extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "review_id", nullable = false)
    private UUID reviewId;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 50)
    private ReportReason reason;

    @Column(name = "description")
    private String description;
}
