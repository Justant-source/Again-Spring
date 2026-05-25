package com.againspring.api.admin.marketing;

import com.againspring.api.dto.response.CalendarItemResponse;
import com.againspring.service.marketing.CalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api/admin/marketing/calendar")
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Marketing Calendar", description = "Marketing content calendar endpoints")
@SecurityRequirement(name = "bearerAuth")
public class CalendarController {

    private final CalendarService calendarService;

    @GetMapping
    @Operation(summary = "Get calendar items for date range")
    public ResponseEntity<List<CalendarItemResponse>> getCalendar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from == null) {
            from = YearMonth.now().atDay(1);
        }
        if (to == null) {
            to = YearMonth.now().plusMonths(1).atDay(1);
        }
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.atStartOfDay(ZoneOffset.UTC).toInstant();
        return ResponseEntity.ok(calendarService.getItems(fromInstant, toInstant));
    }
}
