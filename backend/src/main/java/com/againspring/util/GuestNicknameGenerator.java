package com.againspring.util;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 귀엽고 재치있는 게스트 닉네임 생성기 — "꾸밈말 + 동물" 형식.
 * 예: "노래하는 코끼리", "웃음짓는 개구리"
 */
public final class GuestNicknameGenerator {

    private static final List<String> MODIFIERS = List.of(
            "노래하는", "웃음짓는", "춤추는", "꿈꾸는", "햇살받는",
            "책읽는", "차마시는", "명상하는", "별보는", "구름타는",
            "산책하는", "콧노래부르는", "빵굽는", "꼬리흔드는", "멍때리는",
            "시쓰는", "그림그리는", "깡충거리는", "잠자는", "하품하는",
            "뒹구는", "훌라후프하는", "편지쓰는", "낚시하는", "풍선든"
    );

    private static final List<String> ANIMALS = List.of(
            "코끼리", "개구리", "토끼", "거북이", "다람쥐",
            "너구리", "고슴도치", "판다", "펭귄", "수달",
            "하마", "알파카", "햄스터", "강아지", "고양이",
            "부엉이", "여우", "사슴", "고래", "돌고래",
            "코알라", "카피바라", "나무늘보", "미어캣", "두루미"
    );

    private GuestNicknameGenerator() {}

    public static String generate() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String modifier = MODIFIERS.get(rnd.nextInt(MODIFIERS.size()));
        String animal = ANIMALS.get(rnd.nextInt(ANIMALS.size()));
        return modifier + " " + animal;
    }
}
