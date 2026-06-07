package com.againspring.aiuser.orchestrator.seed;

import java.util.Random;

/**
 * 회원가입 "다른 이름" 버튼과 동일한 닉네임 생성기.
 * 원본: frontend/lib/utils/guestNickname.ts 의 generateGuestNickname().
 * "{꾸밈말} {동물}" 형식 — 예: "노래하는 코끼리", "별보는 판다". LLM 불필요.
 *
 * <p>AI 페르소나도 실유저 게스트와 동일한 닉네임 스타일을 갖도록 한다 (2026-06-07).
 * 풀 변경 시 guestNickname.ts 와 동기화할 것.
 */
public final class GuestStyleNicknameGenerator {

    private GuestStyleNicknameGenerator() {}

    private static final String[] MODIFIERS = {
        "노래하는", "웃음짓는", "춤추는", "꿈꾸는", "햇살받는", "책읽는", "차마시는", "명상하는", "별보는", "구름타는",
        "산책하는", "콧노래부르는", "빵굽는", "꼬리흔드는", "멍때리는", "시쓰는", "그림그리는", "깡충거리는", "잠자는", "하품하는",
        "뒹구는", "훌라후프하는", "편지쓰는", "낚시하는", "풍선든"
    };

    private static final String[] ANIMALS = {
        "코끼리", "개구리", "토끼", "거북이", "다람쥐", "너구리", "고슴도치", "판다", "펭귄", "수달",
        "하마", "알파카", "햄스터", "강아지", "고양이", "부엉이", "여우", "사슴", "고래", "돌고래",
        "코알라", "카피바라", "나무늘보", "미어캣", "두루미"
    };

    /** "{꾸밈말} {동물}" 1개 생성. */
    public static String generate(Random rng) {
        return MODIFIERS[rng.nextInt(MODIFIERS.length)] + " " + ANIMALS[rng.nextInt(ANIMALS.length)];
    }
}
