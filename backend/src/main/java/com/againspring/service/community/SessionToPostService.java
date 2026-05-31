package com.againspring.service.community;

import com.againspring.api.dto.response.SessionDraftDto;
import com.againspring.domain.Session;
import com.againspring.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Phase 5: 세션 → 커뮤니티 사연 전환 서비스
 * 완료된 세션으로부터 익명화된 초안을 추출하여 포스트 작성에 사용
 */
@Service
@RequiredArgsConstructor
public class SessionToPostService {

    private final SessionRepository sessionRepository;

    /**
     * 세션 초안 추출 (참여자만 접근 가능)
     * - 사용자 A/B 라벨을 A님/B님으로 익명화
     * - IssueContext의 headline, facts, namedNeeds, threads를 구조화된 본문으로 변환
     *
     * @param sessionId 세션 ID
     * @param requestUserId 요청 사용자 ID (참여자 검증용)
     * @return SessionDraftDto (제목, 카테고리, 본문)
     * @throws AccessDeniedException 비참여자가 접근할 때
     */
    public SessionDraftDto extractDraft(String sessionId, String requestUserId) {
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new RuntimeException("SESSION_NOT_FOUND"));

        // 참여자 확인 (A 또는 B만 접근 가능)
        boolean isParticipant = requestUserId.equals(session.getCreatedByUserId())
            || requestUserId.equals(session.getInviteeUserId());
        if (!isParticipant) {
            throw new AccessDeniedException("NOT_SESSION_PARTICIPANT");
        }

        // 본문 구성 (IssueContext 활용)
        String bodyRaw = buildAnonymizedBody(session);

        // 카테고리: majorId만 (중·소분류 제거 후 대분류만 유지)
        String category = null;
        if (session.getCategory() != null && session.getCategory().majorId != null) {
            category = session.getCategory().majorId;
        }

        long completedAtEpochSeconds = 0;
        if (session.getCompletedAt() != null) {
            completedAtEpochSeconds = session.getCompletedAt().getEpochSecond();
        }

        return SessionDraftDto.builder()
            .sessionId(sessionId)
            .title(session.getTitle())
            .category(category)
            .bodyRaw(bodyRaw)
            .completedAtEpochSeconds(completedAtEpochSeconds)
            .build();
    }

    /**
     * IssueContext로부터 익명화된 본문 구성
     * headline → facts → namedNeeds → threads 순으로 정렬
     */
    private String buildAnonymizedBody(Session session) {
        StringBuilder sb = new StringBuilder();

        // 1. 세션 제목
        if (session.getTitle() != null && !session.getTitle().isEmpty()) {
            sb.append("주제: ").append(session.getTitle()).append("\n\n");
        }

        // 2. IssueContext가 있으면 활용, 없으면 기본 메시지
        if (session.getIssueContext() != null) {
            Session.IssueContext ctx = session.getIssueContext();

            // 2-1. Headline (핵심 초점)
            if (ctx.headline != null && !ctx.headline.isEmpty()) {
                sb.append("핵심:\n").append(anonymize(ctx.headline)).append("\n\n");
            }

            // 2-2. Facts (관찰된 사실)
            if (ctx.facts != null && !ctx.facts.isEmpty()) {
                sb.append("상황:\n");
                for (Session.IssueFact fact : ctx.facts) {
                    if (fact.text != null) {
                        sb.append("• ").append(anonymize(fact.text)).append("\n");
                    }
                }
                sb.append("\n");
            }

            // 2-3. Named Needs (구체적 욕구)
            if (ctx.namedNeeds != null && !ctx.namedNeeds.isEmpty()) {
                sb.append("욕구:\n");
                for (Session.NeedSlot need : ctx.namedNeeds) {
                    if (need.text != null) {
                        String owner = anonymizeOwner(need.owner);
                        sb.append("• ").append(owner).append(": ").append(anonymize(need.text)).append("\n");
                    }
                }
                sb.append("\n");
            }

            // 2-4. Unresolved Threads (미해결 갈래)
            if (ctx.threads != null && !ctx.threads.isEmpty()) {
                sb.append("남은 질문:\n");
                for (Session.UnresolvedThread thread : ctx.threads) {
                    if (thread.text != null) {
                        sb.append("• ").append(anonymize(thread.text)).append("\n");
                    }
                }
                sb.append("\n");
            }
        } else {
            // IssueContext가 없으면 기본 메시지 추가
            sb.append("이 세션의 대화 내용을 바탕으로 작성되었습니다.\n");
        }

        return sb.toString().trim();
    }

    /**
     * USER_A/USER_B → A님/B님 익명화
     */
    private String anonymize(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace("USER_A", "A님")
            .replace("USER_B", "B님")
            .replace("사용자A", "A님")
            .replace("사용자B", "B님")
            .replace("유저A", "A님")
            .replace("유저B", "B님");
    }

    /**
     * 소유자 필드 (USER_A/USER_B) → (A님/B님)
     */
    private String anonymizeOwner(String owner) {
        if (owner == null) {
            return "누군가";
        }
        return switch (owner) {
            case "USER_A" -> "A님";
            case "USER_B" -> "B님";
            default -> owner;
        };
    }
}
