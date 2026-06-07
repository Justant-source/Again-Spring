package com.againspring.aiuser.orchestrator.seed;

import java.util.Random;

/**
 * 신규 AI 페르소나 닉네임 생성기 — 4가지 스타일을 50:20:20:10 비율로 섞는다 (2026-06-07).
 *  - 꾸밈말+동물 (가입 "다른 이름"과 동일, {@link GuestStyleNicknameGenerator}) … 50%
 *  - 영어+숫자 (예: Sky2847) … 20%
 *  - 바코드형 (예: iliiliiil) … 20%
 *  - 보배드림 스타일 위트 닉 (예: 출근길지옥철) … 10%
 *
 * <p>영어·바코드는 길이 5~10자. 보배드림은 실유저 핸들이 아닌 커뮤니티 위트 스타일 자체 풀.
 * 중복 방지는 호출측(PersonaFactory)이 재시도(새 조합 → 새 스타일)로 처리.
 */
public final class PersonaNicknameGenerator {

    private PersonaNicknameGenerator() {}

    private static final String[] ENG_BASES = {
        "Sky", "Luna", "Rider", "Moon", "Jay", "Pixel", "Neo", "Coco", "Max", "Star",
        "Vibe", "Kai", "Zoe", "Leo", "Milo", "Nova", "Echo", "Rio", "Ace", "Jin",
        "Bao", "Yuki", "Lumi", "Pico", "Toby", "Ruby", "Jett", "Kira", "Finn", "Remy"
    };

    private static final char[] BARCODE = {'i', 'l', 'I', '1'};

    private static final String[] BOBAE = {
        "출근길지옥철", "통장이텅장", "할부의노예", "기름값도둑", "오늘도칼퇴실패", "월요병말기", "내차안나와",
        "퇴근만기다림", "월급은스쳐가", "사축의비애", "점심메뉴고민", "라면이주식", "적금깬날", "보너스어디감",
        "연차쓰고싶다", "카드값폭탄", "텅장요정", "야근의달인", "주말이짧다", "출근하기싫다", "월요일싫어",
        "퇴사꿈나라", "복권이답", "치킨이진리", "배달비아까워", "내집은어디", "통근버스졸기", "칼퇴는전설"
    };

    /** 4스타일 가중 랜덤(50:20:20:10) 1개 생성. */
    public static String generate(Random rng) {
        int r = rng.nextInt(100);
        if (r < 50) return GuestStyleNicknameGenerator.generate(rng); // 꾸밈말+동물
        if (r < 70) return engNum(rng);                               // 영어+숫자
        if (r < 90) return barcode(rng);                              // 바코드
        return BOBAE[rng.nextInt(BOBAE.length)];                      // 보배드림
    }

    private static String engNum(Random rng) {
        StringBuilder sb = new StringBuilder(ENG_BASES[rng.nextInt(ENG_BASES.length)]);
        int digits = 2 + rng.nextInt(3); // 2~4자리
        for (int i = 0; i < digits; i++) sb.append(rng.nextInt(10));
        return sb.toString();
    }

    private static String barcode(Random rng) {
        int n = 6 + rng.nextInt(5); // 6~10자
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(BARCODE[rng.nextInt(BARCODE.length)]);
        return sb.toString();
    }
}
