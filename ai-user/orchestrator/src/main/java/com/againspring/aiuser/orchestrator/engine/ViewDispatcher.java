package com.againspring.aiuser.orchestrator.engine;

import com.againspring.aiuser.orchestrator.client.BackendInternalClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 조회수 보정 — 참여(댓글·좋아요·투표)에 비례해 posts.view_count를 직접 갱신.
 *
 * <p>기존 봇 VIEW 디스패치는 ViewService의 device_id 중복 방지 때문에 페르소나당 글당 1회만
 * 카운트되어 조회수가 과소 집계됐다(글당 ~페르소나 수 cap, 게다가 무작위 분산 → 글마다 0~수회).
 * 현실 커뮤니티는 참여 1건당 수십 명이 조회하므로 denormalized view_count를 참여에 비례해 직접 보정한다.
 *
 * <p>공식: view_count = max(현재, BASE + (8·댓글 + 6·투표) × 글별변동계수)
 * - 좋아요를 입력으로 쓰지 않는다. 조회수→좋아요 순서를 보장해 순환 증폭을 막는다.
 * - 글별변동계수 0.85~1.44 = CRC32(id) 기반 — 글마다 고정이되 서로 다른 자연스러운 분포.
 * - GREATEST로 단조 증가 → 실유저 조회 보존. 매 틱 실행, 참여 증가 시 조회수도 비례 상승.
 *
 * <p>2026-09: posts.view_count 직접 JDBC UPDATE는 users 테이블 직접 쓰기와 같은 위반이었다
 * (orchestrator가 backend DB에 직접 쓰기 + post_views와 count 불변식이 깨짐). 실제 계산·삽입은
 * backend {@code SyntheticViewReconcileService}로 옮기고, 이 클래스는 내부 API만 호출한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewDispatcher {

    private final BackendInternalClient internalClient;

    public int dispatchViews() {
        return internalClient.reconcileViews().orElse(0);
    }
}
