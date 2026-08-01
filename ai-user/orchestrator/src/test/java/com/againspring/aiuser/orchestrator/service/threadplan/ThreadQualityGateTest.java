package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThreadQualityGateTest {

    @Mock private ContentSafetyGuard safetyGuard;

    private ThreadQualityGate gate;

    @BeforeEach
    void setUp() {
        gate = new ThreadQualityGate(safetyGuard);
        when(safetyGuard.check(any(), any())).thenReturn(ContentSafetyGuard.GuardResult.ok());
    }

    @Test
    void stanceCapDropsMajorityUntilShareAtMostEightyPercent() {
        // 5 AUTHOR + 1 NEUTRAL → 83.3% → drop 1 AUTHOR → 4/5 = 80%
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            items.add(item("c" + i, null, "p" + i, "작성자 편 댓글 본문 " + i, "AUTHOR"));
        }
        items.add(item("c6", null, "p6", "중립 댓글 본문입니다", "NEUTRAL"));

        ThreadQualityGate.QualityResult result = gate.evaluate(
                items, Set.of("p1", "p2", "p3", "p4", "p5", "p6"), id -> true,
                1, 1, 0.80);

        assertThat(result.dropped()).isEqualTo(1);
        assertThat(result.keptItems()).hasSize(5);
        long author = result.keptItems().stream()
                .filter(m -> "AUTHOR".equals(m.get("stance")))
                .count();
        assertThat(author).isEqualTo(4);
        assertThat(result.reasons()).anyMatch(r -> r.startsWith("STANCE_CAP:AUTHOR"));
        assertThat(result.passedOperationalMin()).isTrue();
    }

    @Test
    void monocultureStanceDropsAllAndFailsOperationalMin() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            items.add(item("c" + i, null, "p" + i, "같은 관점만 있는 댓글 " + i, "AUTHOR"));
        }

        ThreadQualityGate.QualityResult result = gate.evaluate(
                items, Set.of("p1", "p2", "p3", "p4", "p5", "p6"), id -> true,
                3, 6, 0.80);

        assertThat(result.keptItems()).isEmpty();
        assertThat(result.dropped()).isEqualTo(6);
        assertThat(result.passedOperationalMin()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.startsWith(ThreadQualityGate.FAILURE_QUALITY_BELOW_MIN));
    }

    @Test
    void missingStanceIsUnevaluatedAndDoesNotDrop() {
        List<Map<String, Object>> items = List.of(
                item("c1", null, "p1", "스탠스 없는 댓글 하나", null),
                item("c2", null, "p2", "스탠스 없는 댓글 둘", null),
                item("c3", null, "p3", "스탠스 없는 댓글 셋", null));

        ThreadQualityGate.QualityResult result = gate.evaluate(
                items, Set.of("p1", "p2", "p3"), id -> true, 3, 3);

        assertThat(result.keptItems()).hasSize(3);
        assertThat(result.dropped()).isZero();
        assertThat(result.reasons()).contains(ThreadQualityGate.UNEVALUATED_STANCE);
        assertThat(result.passedOperationalMin()).isTrue();
    }

    @Test
    void dropsOutOfCastAndOrphanReplies() {
        List<Map<String, Object>> items = List.of(
                item("c1", null, "outsider", "캐스트 밖 최상위", "AUTHOR"),
                item("r1", "c1", "p2", "고아 대댓글입니다요", "NEUTRAL"),
                item("c2", null, "p2", "정상 최상위 댓글임", "NEUTRAL"),
                item("c3", null, "p3", "또 다른 최상위 댓글", "COUNTERPART"),
                item("c4", null, "p4", "세 번째 최상위 댓글", "CONTRARIAN"));

        ThreadQualityGate.QualityResult result = gate.evaluate(
                items, Set.of("p2", "p3", "p4"), id -> true, 3, 3);

        assertThat(result.keptItems()).extracting(m -> m.get("ref"))
                .containsExactly("c2", "c3", "c4");
        assertThat(result.reasons()).anyMatch(r -> r.startsWith("CAST:c1"));
        assertThat(result.reasons()).anyMatch(r -> r.startsWith("PARENT:r1"));
        assertThat(result.passedOperationalMin()).isTrue();
    }

    @Test
    void belowReadyMinDoesNotPassEvenWhenItemsKept() {
        List<Map<String, Object>> items = List.of(
                item("c1", null, "p1", "하나뿐인 최상위 댓글", "AUTHOR"),
                item("c2", null, "p2", "둘뿐인 최상위 댓글", "NEUTRAL"));

        ThreadQualityGate.QualityResult result = gate.evaluate(
                items, Set.of("p1", "p2"), id -> true, 3, 6);

        assertThat(result.keptItems()).hasSize(2);
        assertThat(result.passedOperationalMin()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.startsWith(ThreadQualityGate.FAILURE_QUALITY_BELOW_MIN));
    }

    private static Map<String, Object> item(String ref, String parentRef, String personaId,
                                            String body, String stance) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ref", ref);
        if (parentRef != null) m.put("parentRef", parentRef);
        m.put("personaId", personaId);
        m.put("body", body);
        if (stance != null) m.put("stance", stance);
        return m;
    }
}
