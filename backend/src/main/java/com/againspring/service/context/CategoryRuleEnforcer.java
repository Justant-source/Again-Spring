package com.againspring.service.context;

import com.againspring.domain.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Set;

/**
 * Phase D — categories.md §"한국 고유" 룰을 코드화.
 * V47~: 트리거 기준을 session.category.minorId → session.koreanTag(LLM 추론값)로 변경.
 * face(체면) 분기 신규 추가.
 *
 * 권위본: shared/docs/policies/context-algorithm.md §3, shared/docs/policies/categories.md §"한국 고유"
 */
@Slf4j
@Component
public class CategoryRuleEnforcer {

    private static final String IN_LAW = "in_law";
    private static final String FACE = "face";
    private static final String LINGERED = "lingered";
    private static final String GENERATION = "generation";

    private static final Set<String> THIRD_PARTY_JUDGMENT_WORDS = Set.of(
        "잘못", "차별", "못된", "괴롭힘", "악의", "이기적", "나쁜", "심술"
    );

    private static final Set<String> VALUE_HIERARCHY_WORDS = Set.of(
        "구식", "낡은", "잘못된 가치관", "이상한 사고방식", "시대착오", "꼰대"
    );

    // face: 외부 시선·체면 과잉 단어 — "남들이 뭐라 하겠어", "창피", "체면" 등
    private static final Set<String> FACE_ABSOLUTE_WORDS = Set.of(
        "남들한테", "남들이", "창피", "부끄럽게", "망신", "소문", "남들이 보면"
    );

    // lingered: 단일 사건 인터뷰 패턴 키워드 — \b는 한국어에서 동작하지 않아 contains() 사용
    private static final Set<String> SINGLE_EVENT_KEYWORDS = Set.of(
        "어제", "그날", "오늘", "그때", "방금"
    );

    /**
     * Session의 koreanTag를 기반으로 fact 허용 여부 검사.
     * V47~: koreanTag 대신 session.koreanTag 사용.
     * false 반환 시 IssueContextMerger가 추가를 거부.
     */
    public boolean isFactAllowed(Session.IssueFact fact, String koreanTag) {
        if (fact == null || fact.text == null) return false;
        String text = fact.text.toLowerCase();

        // face: 외부 시선·체면 과잉 판단 유발 단어 차단
        if (FACE.equals(koreanTag)) {
            for (String word : FACE_ABSOLUTE_WORDS) {
                if (text.contains(word)) {
                    log.info("Rejected face-category fact (external-judgment): {}", fact.text);
                    return false;
                }
            }
        }

        if (IN_LAW.equals(koreanTag)) {
            for (String word : THIRD_PARTY_JUDGMENT_WORDS) {
                if (text.contains(word)) {
                    log.info("Rejected fact for {} category (third-party judgment): {}", IN_LAW, fact.text);
                    return false;
                }
            }
        }

        if (LINGERED.equals(koreanTag)) {
            for (String kw : SINGLE_EVENT_KEYWORDS) {
                if (text.contains(kw)) {
                    log.info("Rejected single-event fact for {} category: {}", LINGERED, fact.text);
                    return false;
                }
            }
        }

        if (GENERATION.equals(koreanTag)) {
            for (String word : VALUE_HIERARCHY_WORDS) {
                if (text.contains(word)) {
                    log.info("Rejected value-hierarchy fact for {} category: {}", GENERATION, fact.text);
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 카테고리에서 비활성화된 Intent 검사. PR-4 QuestionQueueUpdater에서 사용.
     */
    public boolean isIntentAllowed(Session.Intent intent, String koreanTag) {
        if (LINGERED.equals(koreanTag) && intent == Session.Intent.SEEK_FACT) {
            return false; // 단일 사건 인터뷰 금지
        }
        return true;
    }
}
