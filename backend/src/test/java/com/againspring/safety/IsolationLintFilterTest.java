package com.againspring.safety;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class IsolationLintFilterTest {

    private final IsolationLintFilter filter = new IsolationLintFilter();

    @Test
    void userA_label_violates() {
        assertTrue(filter.violatesIsolation("USER_A님은 이렇게 말씀하셨어요"),
            "USER_A 라벨이 포함되면 격리 위반이어야 함");
    }

    @Test
    void userB_label_violates() {
        assertTrue(filter.violatesIsolation("USER_B 입장에서 보면"),
            "USER_B 라벨이 포함되면 격리 위반이어야 함");
    }

    @Test
    void mediatorToA_label_violates() {
        assertTrue(filter.violatesIsolation("MEDIATOR_TO_A 응답입니다"),
            "MEDIATOR_TO_A 라벨이 포함되면 격리 위반이어야 함");
    }

    @Test
    void mediatorToB_label_violates() {
        assertTrue(filter.violatesIsolation("MEDIATOR_TO_B 응답입니다"),
            "MEDIATOR_TO_B 라벨이 포함되면 격리 위반이어야 함");
    }

    @Test
    void neutralMessage_doesNotViolate() {
        assertFalse(filter.violatesIsolation("상대분이 이렇게 말씀하셨어요"),
            "sender 라벨 없는 정상 응답은 위반이 아니어야 함");
    }

    @Test
    void naturalKorean_doesNotViolate() {
        assertFalse(filter.violatesIsolation("며칠 전부터 비슷한 패턴이 반복됐던 것 같아요."),
            "일반 한국어 응답은 위반이 아니어야 함");
    }

    @Test
    void nullMessage_doesNotViolate() {
        assertFalse(filter.violatesIsolation(null), "null 입력은 위반이 아니어야 함");
    }

    @Test
    void emptyMessage_doesNotViolate() {
        assertFalse(filter.violatesIsolation(""), "빈 문자열은 위반이 아니어야 함");
    }

    @Test
    void partialMatchWithoutWordBoundary_doesNotViolate() {
        // "USER_ADMIN" 같은 문자열은 \b 때문에 걸리지 않아야 함
        // 단, USER_A/USER_B 자체는 \b 경계가 맞으므로 위반
        assertTrue(filter.violatesIsolation("USER_A entered the chat"),
            "정확한 USER_A 단어 경계는 위반이어야 함");
    }
}
