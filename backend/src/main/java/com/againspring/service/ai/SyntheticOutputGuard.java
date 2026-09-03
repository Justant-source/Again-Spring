package com.againspring.service.ai;

import com.againspring.common.exception.BusinessException;
import com.againspring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 서버측 최종 경계 — AI 계정(users.synthetic=1)이 올리는 본문이 LLM 오류·거절·누출이면 게시를 거부한다.
 * 콘텐츠 검열이 아니다: 표현·욕설·주제는 보지 않는다. 실사용자에게는 절대 적용하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyntheticOutputGuard {
    public static final String CODE = "LLM_ERROR_OUTPUT";
    private final UserRepository userRepository;

    /**
     * 작성자가 AI 계정(users.synthetic=1)인지 판정하면서, synthetic이면 같은 조회 결과로 게시 가능 여부까지 검사한다.
     * UserRepository 조회는 이 메서드 안에서 딱 1회만 일어난다 — 호출부가 별도로 isSynthetic을 다시 조회하지 않도록.
     *
     * @return authorId가 synthetic 작성자이면 true(검사 통과 시), 아니면(null 포함) false.
     * @throws BusinessException synthetic 작성자의 본문이 LLM 오류·거절·누출 시그니처를 포함하면 422
     */
    public boolean assertPublishableIfSynthetic(String authorId, String body) {
        if (authorId == null) return false;
        boolean synthetic = userRepository.findById(authorId).map(u -> u.isSynthetic()).orElse(false);
        if (!synthetic || body == null) return synthetic;

        LlmErrorSignatures s = LlmErrorSignatures.get();
        String reason = null;
        if (s.containsSignature(body.toLowerCase(Locale.ROOT))) reason = "LLM_ERROR_SIGNATURE";
        else if (s.hasInsufficientKorean(body)) reason = "INSUFFICIENT_KOREAN";
        else if (s.hasPromptLeak(body)) reason = "PROMPT_LEAK_META";
        if (reason != null) {
            log.error("SyntheticOutputGuard: rejected synthetic author={} reason={} len={}", authorId, reason, body.length());
            throw new BusinessException(CODE, "AI 출력 오류 문자열은 게시할 수 없습니다 (" + reason + ")", 422);
        }
        return true;
    }
}
