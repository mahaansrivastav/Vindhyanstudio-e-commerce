/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.analytics;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.vindhyanstudio.common.CurrentUser;

import jakarta.servlet.http.HttpServletRequest;

/**
 * /api/t/pv       public: the website reports a page view
 * /api/analytics  ANALYTICS or DEVELOPER: aggregate numbers only
 * /api/dev        DEVELOPER only: logins, visitors and page views with IPs and emails, Excel export
 */
@RestController
public class AnalyticsController {

    private final TrackingService tracking;
    private final AnalyticsService analytics;

    public AnalyticsController(TrackingService tracking, AnalyticsService analytics) {
        this.tracking = tracking;
        this.analytics = analytics;
    }

    @PostMapping("/api/t/pv")
    ResponseEntity<Void> pageView(@RequestBody TrackingService.PageView body, HttpServletRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long customer = auth != null && auth.getPrincipal() instanceof Jwt jwt ? Long.valueOf(jwt.getSubject()) : null;
        tracking.pageView(body, customer, req);
        return ResponseEntity.noContent().build();
    }

    /** Developers also see admin and developer-console traffic when {@code internal=true}. */
    @GetMapping("/api/analytics/overview")
    AnalyticsService.Overview overview(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
                                       @RequestParam(defaultValue = "false") boolean internal) {
        return analytics.overview(from, to, internal && CurrentUser.hasRole("DEVELOPER"));
    }

    @GetMapping("/api/dev/logins")
    List<AnalyticsService.Login> logins(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return analytics.logins(from, to);
    }

    @GetMapping("/api/dev/visitors")
    List<AnalyticsService.Visitor> visitors(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return analytics.visitors(from, to);
    }

    @GetMapping("/api/dev/visits")
    List<AnalyticsService.Visit> visits(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return analytics.visits(from, to);
    }

    @GetMapping("/api/dev/export")
    ResponseEntity<byte[]> export(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                  @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) throws IOException {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("usage-" + from + "-to-" + to + ".xlsx").build().toString())
                .body(analytics.export(from, to));
    }
}
