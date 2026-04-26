package com.againspring.service.crisis;

import org.springframework.stereotype.Service;
import java.util.List;

/**
 * CrisisDetector (V1.5)
 * 사용자 입력에서 위기 키워드를 감지
 * shared/docs/policies/crisis-detection.md 기반
 */
@Service
public class CrisisDetector {

    private static final List<String> LEVEL_1_KEYWORDS = List.of(
        "때리", "맞았", "학대", "폭행", "때렸", "강간", "성폭행",
        "죽고 싶", "죽어버", "자해", "뛰어내리", "목매"
    );

    private static final List<String> LEVEL_2_KEYWORDS = List.of(
        "이혼", "소송", "위자료", "양육권"
    );

    public CrisisInfo detect(String content) {
        if (content == null) return new CrisisInfo(0, null);

        String lower = content.toLowerCase();

        // Level 1: 즉시 대응 필요 (폭력, 자해)
        for (var kw : LEVEL_1_KEYWORDS) {
            if (lower.contains(kw)) return new CrisisInfo(1, kw);
        }

        // Level 2: 경고 수준 (법적 이슈)
        for (var kw : LEVEL_2_KEYWORDS) {
            if (lower.contains(kw)) return new CrisisInfo(2, kw);
        }

        return new CrisisInfo(0, null);
    }

    public record CrisisInfo(int level, String matchedKeyword) {}
}
