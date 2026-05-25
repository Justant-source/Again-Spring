package com.againspring.service.marketing;

import com.againspring.api.dto.response.SimulationResponse;
import com.againspring.api.dto.response.SimulationSummaryResponse;
import com.againspring.domain.marketing.MarketingSimulation;
import com.againspring.domain.marketing.MarketingSourceStory;
import com.againspring.repository.marketing.MarketingSimulationRepository;
import com.againspring.repository.marketing.MarketingSourceStoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 마케팅 시뮬레이션 비즈니스 로직.
 * V15.3: 시뮬레이션 생성, 조회, 취소.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class SimulationService {

    private final MarketingSimulationRepository simRepo;
    private final MarketingSourceStoryRepository storyRepo;
    private final SimulationOrchestrator orchestrator;

    /**
     * 승인된 스토리로부터 새 시뮬레이션을 시작.
     *
     * @param storyId 스토리 ID
     * @return 시뮬레이션 응답 (상태: QUEUED)
     * @throws IllegalArgumentException 스토리를 찾을 수 없거나 승인되지 않은 경우
     */
    @Transactional
    public SimulationResponse startSimulation(Long storyId) {
        // 1. 스토리 로드 및 검증
        MarketingSourceStory story = storyRepo.findById(storyId)
            .orElseThrow(() -> new IllegalArgumentException("Story not found: " + storyId));

        if (!story.getStatus().equals(MarketingSourceStory.Status.APPROVED)) {
            throw new IllegalArgumentException("Story is not approved: " + story.getStatus());
        }

        // 2. 시뮬레이션 생성
        MarketingSimulation simulation = MarketingSimulation.builder()
            .sourceStoryId(storyId)
            .turnCount(8)
            .actualTurnCount(null)
            .status(MarketingSimulation.Status.QUEUED)
            .build();

        MarketingSimulation saved = simRepo.save(simulation);
        log.info("Simulation {} created for story {}", saved.getId(), storyId);

        // 3. 비동기 실행 시작
        orchestrator.runSimulation(saved.getId());

        // 4. 응답 반환
        return SimulationResponse.from(saved);
    }

    /**
     * 모든 시뮬레이션을 상태 필터링과 함께 조회.
     *
     * @param status 상태 필터 (선택사항)
     * @return 요약 응답 목록
     */
    @Transactional(readOnly = true)
    public List<SimulationSummaryResponse> findAll(String status) {
        Pageable pageable = PageRequest.of(0, 100, Sort.by("id").descending());

        Page<MarketingSimulation> page;
        if (status == null || status.isBlank()) {
            page = simRepo.findAll(pageable);
        } else {
            try {
                MarketingSimulation.Status statusEnum = MarketingSimulation.Status.valueOf(status.toUpperCase());
                page = simRepo.findByStatus(statusEnum, pageable);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status filter: {}", status);
                page = simRepo.findAll(pageable);
            }
        }

        return page.getContent().stream()
            .map(SimulationSummaryResponse::from)
            .collect(Collectors.toList());
    }

    /**
     * 단일 시뮬레이션 조회.
     *
     * @param id 시뮬레이션 ID
     * @return 상세 응답
     * @throws IllegalArgumentException 시뮬레이션을 찾을 수 없는 경우
     */
    @Transactional(readOnly = true)
    public SimulationResponse findById(Long id) {
        MarketingSimulation simulation = simRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Simulation not found: " + id));
        return SimulationResponse.from(simulation);
    }

    /**
     * 시뮬레이션 취소.
     * QUEUED 또는 RUNNING 상태일 때만 취소 가능.
     *
     * @param id 시뮬레이션 ID
     * @return 업데이트된 응답
     * @throws IllegalArgumentException 시뮬레이션을 찾을 수 없는 경우
     * @throws IllegalStateException 취소 불가능한 상태인 경우
     */
    @Transactional
    public SimulationResponse cancel(Long id) {
        MarketingSimulation simulation = simRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Simulation not found: " + id));

        if (simulation.getStatus().equals(MarketingSimulation.Status.QUEUED) ||
            simulation.getStatus().equals(MarketingSimulation.Status.RUNNING)) {
            simulation.setStatus(MarketingSimulation.Status.CANCELED);
            MarketingSimulation updated = simRepo.save(simulation);
            log.info("Simulation {} canceled", id);
            return SimulationResponse.from(updated);
        } else {
            throw new IllegalStateException("Cannot cancel simulation in status: " + simulation.getStatus());
        }
    }

    @Transactional
    public void delete(Long id) {
        MarketingSimulation simulation = simRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Simulation not found: " + id));

        if (simulation.getStatus().equals(MarketingSimulation.Status.QUEUED) ||
            simulation.getStatus().equals(MarketingSimulation.Status.RUNNING)) {
            throw new IllegalStateException("Cannot delete simulation in status: " + simulation.getStatus());
        }

        simRepo.deleteById(id);
        log.info("Simulation {} deleted", id);
    }
}
