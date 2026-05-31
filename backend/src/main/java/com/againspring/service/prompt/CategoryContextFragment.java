package com.againspring.service.prompt;

import com.againspring.domain.Session;
import com.againspring.service.category.CategoryCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 세션 카테고리(대분류 + 직접 입력)를 LLM 프롬프트에 주입.
 * V47~: 중·소분류 제거 — majorId + customText만 잔존.
 *
 * 카테고리 미설정 또는 catalog 매칭 실패 시 빈 문자열 반환 — 채팅 회귀 없음.
 */
@Component
@RequiredArgsConstructor
public class CategoryContextFragment {

    private final CategoryCatalog catalog;

    public String render(Session session) {
        if (session == null || session.getCategory() == null) return "";
        Session.Category c = session.getCategory();
        if (c.majorId == null) return "";

        CategoryCatalog.MajorCategory major = catalog.getMajor(c.majorId);
        if (major == null) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<conflict_category note=\"사용자가 세션 시작 시 선택한 관계 유형. ")
          .append("이를 인지하여 관계 유형에 적합한 후속 질문을 생성하세요. ")
          .append("단, '당신이 부부 카테고리를 선택하셨네요' 같이 라벨을 직접 인용하지는 않습니다.\">\n");
        sb.append("- 관계 유형: ").append(major.getLabel()).append("\n");

        if (c.customText != null && !c.customText.isBlank()) {
            sb.append("- 사용자 추가 설명: ").append(c.customText.strip()).append("\n");
        }

        sb.append("</conflict_category>\n");
        return sb.toString();
    }
}
