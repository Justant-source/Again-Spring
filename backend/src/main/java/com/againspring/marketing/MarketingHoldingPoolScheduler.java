package com.againspring.marketing;

import com.againspring.marketing.holding.MarketingHoldingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 24h 홀딩 풀에 새 사연을 적재한다.
 *
 * <p><b>왜 필요한가</b> — 적재는 {@link MarketingHoldingService#getBoard()} 안에서만 일어나는데,
 * 그 메서드를 부르는 곳이 어드민 「대기」 탭 조회 하나뿐이었다. 사람이 화면을 열지 않으면
 * 풀이 비고, 그러면 커밋 틱({@link XThreadPublishTriggerScheduler})이 매번 "due 없음"으로 끝나
 * 영상 잡이 한 건도 만들어지지 않는다.
 *
 * <p>실측(2026-08-25): 사연은 매일 8~11건씩 정상 생성되는데 홀딩 적재는 08-23 이후 0건,
 * 그 결과 쇼츠·릴스 잡이 하루 종일 0건이었다. 날짜별로 보면 08-16·19·20·21에도 같은 공백이
 * 있었다 — 어드민이 탭을 연 날에만 파이프라인이 돌고 있었다.
 *
 * <p><b>opt-in 게이트</b> — ASM 은 dev·prod 가 공유하는 단일 인스턴스라, 무인 스케줄러는
 * 기본 false 로 두고 의도한 환경에서만 켠다(2026-07-31 실계정 오발행 사고 이후 규칙).
 * 이 스케줄러 자체는 발행하지 않고 풀에만 적재하지만, 적재분은 T+24h 뒤 커밋 틱이 집어가므로
 * 결국 발행으로 이어진다. 같은 기준을 적용한다.
 *
 * <p>적재는 DB 조회만 한다({@code MarketingHoldingBriefSeeder} 는 투표·댓글·작성자를 읽을 뿐
 * LLM 을 호출하지 않는다). 주기적으로 돌려도 토큰 비용이 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketingHoldingPoolScheduler {

    private final MarketingHoldingService holdingService;

    /**
     * 무인 실행 opt-in. 기본 false — dev 를 그냥 재배포해도 자동으로 돌지 않는다.
     */
    @Value("${marketing.holding-pool-refresh-enabled:false}")
    private boolean refreshEnabled;

    /**
     * 새 사연을 홀딩 풀에 적재한다. 기본 주기 10분 — 커밋 틱과 같은 간격이라
     * 적재된 사연이 다음 커밋 틱에 늦지 않게 잡힌다.
     *
     * <p>{@code getBoard()} 는 내부에서 락과 트랜잭션을 잡고 재시도까지 한다.
     * 어드민이 같은 시각에 탭을 열어도 서로 밀어내지 않는다.
     */
    @Scheduled(fixedDelayString = "${marketing.holding-pool-refresh-interval-ms:600000}")
    public void refreshPool() {
        if (!refreshEnabled) {
            log.debug("홀딩 풀 적재 스케줄러 꺼짐 (marketing.holding-pool-refresh-enabled=false)");
            return;
        }
        try {
            MarketingHoldingService.HoldingBoard board = holdingService.getBoard();
            int items = board.items() != null ? board.items().size() : 0;
            log.info("[holding-pool] 적재 완료 — 보드 {}건", items);
        } catch (Exception e) {
            // 실패해도 다음 주기에 다시 시도한다. 조용히 죽으면 오늘 같은 공백을
            // 또 며칠씩 못 알아챈다.
            log.error("[holding-pool] 적재 실패 — 다음 주기에 재시도", e);
        }
    }
}
