package com.matjom.matjom.visit.dto;

import com.matjom.matjom.visit.entity.ClientMode;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 방문 세션을 시작할 때 필요한 입력값을 담는 DTO.
 * 사용 목적: 어떤 장소에 어떤 모드로 세션을 열지 검증해 서비스로 전달한다.
 * 코드 의미: `clientMode`가 비어 있으면 기본값 NAVIGATION을 적용해 클라이언트 설정 누락을 방어한다.
 */
@Getter
@Setter
public class VisitSessionStartRequest {

    @NotNull(message = "placeId는 필수입니다.")
    private Long placeId;

    @Size(max = 50, message = "clientNote는 50자 이하여야 합니다.")
    private String clientNote;

    private ClientMode clientMode;

    public ClientMode clientModeOrDefault() {
        if (clientMode == null) {
            return ClientMode.NAVIGATION;
        }
        return clientMode;
    }
}
