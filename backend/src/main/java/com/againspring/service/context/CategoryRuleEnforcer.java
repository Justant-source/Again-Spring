package com.againspring.service.context;

import com.againspring.domain.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Set;

/**
 * Phase D — categories.md §"한국 고유" 룰을 코드화.
 * 카테고리별 fact/intent 제약을 검증하고 위반 시 거부(false 반환 + 로그).
 *
 * 권위본: shared/docs/policies/context-algorithm.md §3, shared/docs/policies/categories.md §"한국 고유"
 */
@Slf4j
@Component
public class CategoryRuleEnforcer {

    private static final String IN_LAW = "in_law";
    private static final String LINGERED = "lingered";
    private static final String GENERATION = "generation";

    private static final Set<String> THIRD_PARTY_JUDGMENT_WORDS = Set.of(
        "잘못", "차별", "못된", "괴롭힘", "악의", "이기적", "나쁜", "심술"
    );

    private static final Set<String> VALUE_HIERARCHY_WORDS = Set.of(
        "구식", "낡은", "잘못된 가치관", "이상한 사고방식", "시대착오", "꼰대"
    );

    // lingered: 단일 사건 인터뷰 패턴 키워드 — \b는 한국어에서 동작하지 않아 contains() 사용
    private static final Set<String> SINGLE_EVENT_KEYWORDS = Set.of(
        "어제", "그날", "오늘", "그때", "방금"
    );

    /**
     * fact가 해당 카테고리에서 허용되는지 검사.
     * false 반환 시 IssueContextMerger가 추가를 거부.
     */
    public boolean isFactAllowed(Session.IssueFact fact, String categoryMinorId) {
        if (fact == null || fact.text == null) return false;
        String text = fact.text.toLowerCase();

        if (IN_LAW.equals(categoryMinorId)) {
            for (String word : THIRD_PARTY_JUDGMENT_WORDS) {
                if (text.contains(word)) {
                    log.info("Rejected fact for {} category (third-party judgment): {}", IN_LAW, fact.text);
                    return false;
                }
            }
        }

        if (LINGERED.equals(categoryMinorId)) {
            for (String kw : SINGLE_EVENT_KEYWORDS) {
                if (text.contains(kw)) {
                    log.info("Rejected single-event fact for {} category: {}", LINGERED, fact.text);
                    return false;
                }
            }
        }

        if (GENERATION.equals(categoryMinorId)) {
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
    public boolean isIntentAllowed(Session.Intent intent, String categoryMinorId) {
        if (LINGERED.equals(categoryMinorId) && intent == Session.Intent.SEEK_FACT) {
            return false; // 단일 사건 인터뷰 금지
        }
        return true;
    }
}
