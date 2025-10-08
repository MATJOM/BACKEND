package com.matjom.matjom.visit.controller;

import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.visit.dto.VisitListResponseDTO;
import com.matjom.matjom.visit.service.VisitHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/visits")
@RequiredArgsConstructor
public class VisitController {

    private final VisitHistoryService visitHistoryService;

    @GetMapping("/my")
    public ApiResponse<VisitListResponseDTO> getMyVisits(
            @RequestAttribute UUID userId,
            @RequestParam(required = false) String keyword
    ) {
        VisitListResponseDTO response = visitHistoryService.getMyArrivedVisits(userId, keyword);
        return ApiResponse.ok(response);
    }
}
