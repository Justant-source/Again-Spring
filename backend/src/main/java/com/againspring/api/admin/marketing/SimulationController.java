package com.againspring.api.admin.marketing;

import com.againspring.api.dto.response.MessageResponse;
import com.againspring.api.dto.response.ReportResponse;
import com.againspring.api.dto.response.SimulationResponse;
import com.againspring.api.dto.response.SimulationSummaryResponse;
import com.againspring.domain.marketing.MarketingSimulation;
import com.againspring.repository.MessageRepository;
import com.againspring.repository.ReportRepository;
import com.againspring.repository.marketing.MarketingSimulationRepository;
import com.againspring.service.marketing.SimulationService;
import com.againspring.service.report.ReportResponseMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 마케팅 시뮬레이션 관리 컨트롤러.
 * V15.3: 승인된 스토리를 기반으로 AI 중재 시뮬레이션을 생성, 조회, 취소.
 */
@Slf4j
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RestController
@RequestMapping("/api/admin/marketing/simulations")
@RequiredArgsConstructor
@Tag(name = "Admin — Marketing Simulations (V15.3)", description = "마케팅 시뮬레이션 관리 (ADMIN 전용)")
@SecurityRequirement(name = "bearer-jwt")
public class SimulationController {

    private final SimulationService simulationService;
    private final MarketingSimulationRepository simRepo;
    private final MessageRepository messageRepository;
    private final ReportRepository reportRepository;
    private final ReportResponseMapper reportMapper;

    /**
     * 새 시뮬레이션 시작.
     * 승인된 스토리로부터 7~10턴의 AI 중재 대화 생성.
     *
     * @param storyId 스토리 ID
     * @return 201 Created: 시뮬레이션 응답 (상태: QUEUED)
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "새 시뮬레이션 시작",
        description = "승인된 스토리를 기반으로 7~10턴의 AI 중재 시뮬레이션을 생성하고 비동기 실행을 시작한다."
    )
    @ApiResponse(
        responseCode = "201",
        description = "시뮬레이션 생성 완료 (상태: QUEUED)",
        content = @Content(schema = @Schema(implementation = SimulationResponse.class))
    )
    @ApiResponse(responseCode = "400", description = "스토리를 찾을 수 없거나 승인되지 않음")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<SimulationResponse> startSimulation(
            @Parameter(description = "스토리 ID", required = true)
            @RequestParam Long storyId) {
        try {
            SimulationResponse response = simulationService.startSimulation(storyId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid story ID or status: {}", storyId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 모든 시뮬레이션 조회.
     * 상태 필터링 지원.
     *
     * @param status 상태 필터 (선택사항): QUEUED, RUNNING, COMPLETED, FAILED, CANCELED
     * @return 200 OK: 시뮬레이션 요약 목록
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "시뮬레이션 목록 조회",
        description = "모든 시뮬레이션을 상태 필터링과 함께 조회한다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "시뮬레이션 요약 목록",
        content = @Content(schema = @Schema(implementation = SimulationSummaryResponse.class))
    )
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<List<SimulationSummaryResponse>> listSimulations(
            @Parameter(description = "상태 필터 (선택사항)", example = "COMPLETED")
            @RequestParam(required = false) String status) {
        List<SimulationSummaryResponse> responses = simulationService.findAll(status);
        return ResponseEntity.ok(responses);
    }

    /**
     * 단일 시뮬레이션 조회.
     *
     * @param id 시뮬레이션 ID
     * @return 200 OK: 시뮬레이션 상세 응답
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "시뮬레이션 상세 조회",
        description = "특정 시뮬레이션의 전체 정보를 조회한다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "시뮬레이션 상세 정보",
        content = @Content(schema = @Schema(implementation = SimulationResponse.class))
    )
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    @ApiResponse(responseCode = "404", description = "시뮬레이션을 찾을 수 없음")
    public ResponseEntity<SimulationResponse> getSimulation(
            @Parameter(description = "시뮬레이션 ID", required = true)
            @PathVariable Long id) {
        try {
            SimulationResponse response = simulationService.findById(id);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Simulation not found: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 시뮬레이션 취소.
     * QUEUED 또는 RUNNING 상태일 때만 취소 가능.
     *
     * @param id 시뮬레이션 ID
     * @return 200 OK: 업데이트된 시뮬레이션 응답 (상태: CANCELED)
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "시뮬레이션 취소",
        description = "QUEUED 또는 RUNNING 상태의 시뮬레이션을 취소한다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "시뮬레이션 취소 완료 (상태: CANCELED)",
        content = @Content(schema = @Schema(implementation = SimulationResponse.class))
    )
    @ApiResponse(responseCode = "400", description = "취소할 수 없는 상태")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    @ApiResponse(responseCode = "404", description = "시뮬레이션을 찾을 수 없음")
    public ResponseEntity<SimulationResponse> cancelSimulation(
            @Parameter(description = "시뮬레이션 ID", required = true)
            @PathVariable Long id) {
        try {
            SimulationResponse response = simulationService.cancel(id);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Simulation not found: {}", id, e);
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            log.warn("Cannot cancel simulation {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 시뮬레이션 대화 메시지 목록 조회 (admin 전용).
     * 시뮬레이션의 세션 ID를 통해 실제 Message 엔티티를 반환.
     *
     * @param id 시뮬레이션 ID
     * @return 200: 메시지 목록, 404: 시뮬레이션 또는 세션 없음
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "시뮬레이션 삭제", description = "QUEUED/RUNNING 상태가 아닌 시뮬레이션을 영구 삭제한다.")
    @ApiResponse(responseCode = "204", description = "삭제 완료")
    @ApiResponse(responseCode = "400", description = "실행 중 삭제 불가")
    @ApiResponse(responseCode = "404", description = "시뮬레이션을 찾을 수 없음")
    public ResponseEntity<Void> deleteSimulation(@PathVariable Long id) {
        try {
            simulationService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}/messages")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "시뮬레이션 대화 메시지 목록",
        description = "COMPLETED 시뮬레이션의 채팅 메시지를 시간순으로 반환한다 (지난 대화 뷰어용)."
    )
    @ApiResponse(responseCode = "200", description = "메시지 목록")
    @ApiResponse(responseCode = "404", description = "시뮬레이션 없음 또는 세션 미연결")
    public ResponseEntity<List<MessageResponse>> getSimulationMessages(@PathVariable Long id) {
        return simRepo.findById(id)
                .filter(sim -> sim.getSessionId() != null)
                .map(sim -> messageRepository.findBySessionIdOrderByCreatedAtAsc(sim.getSessionId())
                        .stream().map(MessageResponse::from).toList())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 시뮬레이션 결과 리포트 조회 (admin 전용).
     * 시뮬레이션의 세션 ID에 연결된 Solo 리포트를 반환.
     *
     * @param id 시뮬레이션 ID
     * @return 200: 리포트, 404: 시뮬레이션/세션/리포트 없음
     */
    @GetMapping("/{id}/report")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "시뮬레이션 결과 리포트 조회",
        description = "COMPLETED 시뮬레이션에 연결된 Solo 리포트를 반환한다."
    )
    @ApiResponse(responseCode = "200", description = "리포트 상세")
    @ApiResponse(responseCode = "404", description = "리포트 없음 또는 아직 생성 중")
    public ResponseEntity<ReportResponse> getSimulationReport(@PathVariable Long id) {
        return simRepo.findById(id)
                .filter(sim -> sim.getSessionId() != null)
                .flatMap(sim -> reportRepository.findBySessionId(sim.getSessionId()))
                .map(report -> ResponseEntity.ok(reportMapper.toResponse(report)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
