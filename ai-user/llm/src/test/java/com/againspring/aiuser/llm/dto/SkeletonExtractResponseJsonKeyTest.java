package com.againspring.aiuser.llm.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * persona-diversity-v4 잔여 결함 #1 회귀 테스트.
 * Lombok 게터 이름 맹글링("bSideViable" -> "getBSideViable" -> Jackson 기본
 * 맹글링으로 "bsideViable")이 필드의 명시적 @JsonProperty("b_side_viable")와
 * 어긋나 키가 중복 출력되던 문제를 검증한다. 정식 키는 계약7의 "b_side_viable" 하나뿐이어야 한다.
 */
class SkeletonExtractResponseJsonKeyTest {
    private final ObjectMapper om = new ObjectMapper();

    @Test
    void serializesSingleBSideViableKey() throws Exception {
        SkeletonExtractResponse r = SkeletonExtractResponse.builder()
            .ok(true)
            .bSideViable(false)
            .build();

        JsonNode node = om.readTree(om.writeValueAsString(r));

        assertTrue(node.has("b_side_viable"), "정식 키 b_side_viable 부재: " + node);
        assertFalse(node.has("bsideViable"), "중복 키 bsideViable 잔존: " + node);
        assertFalse(node.has("bSideViable"), "중복 키 bSideViable 잔존: " + node);
        assertFalse(node.get("b_side_viable").asBoolean());
    }

    @Test
    void getterStillAccessibleForExistingCallers() {
        SkeletonExtractResponse r = SkeletonExtractResponse.builder()
            .ok(true)
            .bSideViable(false)
            .build();
        // StructuredGenerationService.java:840 등 기존 소비자가 getBSideViable()을
        // 그대로 호출하므로 시그니처가 유지돼야 한다.
        assertEquals(Boolean.FALSE, r.getBSideViable());
    }
}
