package com.matjom.matjom.feed.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EligibilityCheckResponseDTO {
    private Boolean eligible;             // 작성 가능 여부
    private String reason;               // 불가능한 경우 이유
    private Long visitId;                // 해당 방문 ID
    private Boolean visitArrived;        // 도착 여부
    private Boolean alreadyWritten;      // 이미 작성 여부
    private Boolean withinTimeLimit;     // 시간 제한 내 여부

    public static EligibilityCheckResponseDTO eligible(Long visitId) {
        return EligibilityCheckResponseDTO.builder()
                .eligible(true)
                .reason(null)
                .visitId(visitId)
                .visitArrived(true)
                .alreadyWritten(false)
                .withinTimeLimit(true)
                .build();
    }

    public static EligibilityCheckResponseDTO notEligible(String reason, Long visitId,
                                                       Boolean visitArrived, Boolean alreadyWritten, Boolean withinTimeLimit) {
        return EligibilityCheckResponseDTO.builder()
                .eligible(false)
                .reason(reason)
                .visitId(visitId)
                .visitArrived(visitArrived)
                .alreadyWritten(alreadyWritten)
                .withinTimeLimit(withinTimeLimit)
                .build();
    }

}
