package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.DashboardAnalyticsDto;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/analytics")
    public ResponseEntity<DashboardAnalyticsDto> getAnalytics(@AuthenticationPrincipal UserPrincipal principal) {
        Long tenantId = principal.getTenantId();
        DashboardAnalyticsDto analytics = dashboardService.getAnalytics(tenantId);
        return ResponseEntity.ok(analytics);
    }
}
