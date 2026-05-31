package com.againspring.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Session.Category 역직렬화 호환성.
 * V47에서 middleId/minorId를 제거했으나 prod DB에는 옛 형식 JSON이 남아 있어,
 * @JsonIgnoreProperties(ignoreUnknown=true)로 무시하지 않으면 옛 세션 로드 시
 * UnrecognizedPropertyException → 계정 삭제·세션 조회 실패.
 */
class SessionCategoryTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void legacyJson_withRemovedMiddleAndMinorIds_isDeserializedIgnoringUnknownFields() {
        // V47 이전 prod 데이터 형식 (실제 오류를 일으킨 값)
        String legacyJson =
                "{\"majorId\":\"marriage\",\"middleId\":\"marriage_inlaws\",\"minorId\":\"visit_freq\",\"customText\":null}";

        Session.Category cat = assertDoesNotThrow(
                () -> mapper.readValue(legacyJson, Session.Category.class));

        assertThat(cat.majorId).isEqualTo("marriage");
        assertThat(cat.customText).isNull();
    }

    @Test
    void currentJson_isDeserializedNormally() throws Exception {
        String json = "{\"majorId\":\"couple\",\"customText\":\"직접 입력한 상황\"}";

        Session.Category cat = mapper.readValue(json, Session.Category.class);

        assertThat(cat.majorId).isEqualTo("couple");
        assertThat(cat.customText).isEqualTo("직접 입력한 상황");
    }
}
