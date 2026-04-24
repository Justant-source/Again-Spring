package com.againspring.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Communication style calculator based on 10-question onboarding.
 * Maps Likert scale answers to 6 communication styles.
 */
@Component
public class StyleCalculator {

    public enum CommunicationStyle {
        WAVE("wave", "🌊", "파도형",
                "감정 표현이 풍부하고 즉각적인 스타일",
                List.of("진솔한 감정 표현", "따뜻한 공감 능력"),
                List.of("감정 격앙 시 휴식 필요", "상대에게 숨 돌릴 시간 주기")),

        MOUNTAIN("mountain", "🏔️", "산형",
                "차분하고 거리를 두고 생각하는 스타일",
                List.of("평정심", "신중한 판단"),
                List.of("표현 부족으로 오해 가능", "감정 공유 노력 필요")),

        FLAME("flame", "🔥", "불꽃형",
                "직설적이고 명확함을 선호하는 스타일",
                List.of("명확한 의사 전달", "빠른 문제 해결"),
                List.of("말투가 상처될 수 있음", "부드러운 시작 필요")),

        LEAF("leaf", "🌿", "이파리형",
                "조화와 공감을 중시하는 스타일",
                List.of("뛰어난 공감력", "관계 조율 능력"),
                List.of("자기 욕구 표현 부족", "솔직한 의사 표현 연습")),

        MOON("moon", "🌙", "달빛형",
                "말보다 분위기·행동으로 표현하는 스타일",
                List.of("세심한 배려", "행동을 통한 사랑"),
                List.of("상대가 오해할 수 있음", "말로도 표현해주세요")),

        STAR("star", "⭐", "별빛형",
                "논리와 이유를 중시하는 스타일",
                List.of("구조적 사고", "근거 있는 대화"),
                List.of("감정 인정 먼저 하기", "상대 감정 덮어쓰지 않기"));

        private final String value;
        private final String emoji;
        private final String label;
        private final String description;
        private final List<String> strengths;
        private final List<String> caution;

        CommunicationStyle(String value, String emoji, String label, String description,
                List<String> strengths, List<String> caution) {
            this.value = value;
            this.emoji = emoji;
            this.label = label;
            this.description = description;
            this.strengths = strengths;
            this.caution = caution;
        }

        public String getValue() {
            return value;
        }

        public String getEmoji() {
            return emoji;
        }

        public String getLabel() {
            return label;
        }

        public String getDescription() {
            return description;
        }

        public List<String> getStrengths() {
            return strengths;
        }

        public List<String> getCaution() {
            return caution;
        }
    }

    /**
     * Calculate communication style from 10-question onboarding answers.
     *
     * @param answers list of 10 integers (1-5 each)
     * @return computed CommunicationStyle
     * @throws IllegalArgumentException if answers length != 10 or invalid range
     */
    public CommunicationStyle calculateStyle(List<Integer> answers) {
        if (answers == null || answers.size() != 10) {
            throw new IllegalArgumentException("Exactly 10 answers required");
        }

        // Validate each answer is 1-5
        for (Integer answer : answers) {
            if (answer == null || answer < 1 || answer > 5) {
                throw new IllegalArgumentException("Each answer must be between 1 and 5");
            }
        }

        Map<String, Double> axes = calculateAxes(answers);

        // Find style with highest score
        String topStyle = axes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("wave");

        return CommunicationStyle.valueOf(topStyle.toUpperCase());
    }

    private Map<String, Double> calculateAxes(List<Integer> answers) {
        Integer q1 = answers.get(0);
        Integer q2 = answers.get(1);
        Integer q3 = answers.get(2);
        Integer q4 = answers.get(3);
        Integer q5 = answers.get(4);
        Integer q6 = answers.get(5);
        Integer q7 = answers.get(6);
        Integer q8 = answers.get(7);
        Integer q9 = answers.get(8);
        Integer q10 = answers.get(9);

        Map<String, Double> axes = new HashMap<>();

        // 파도형: 감정 표현 강함 + 빠른 감정 반응
        Double wave = ((6.0 - q1) + q2 + (6.0 - q5)) / 3.0 * 2.0;
        axes.put("wave", wave);

        // 산형: 신중하고 거리를 두는 경향
        Double mountain = (q1 + (6.0 - q2)) / 2.0 * 2.0;
        axes.put("mountain", mountain);

        // 불꽃형: 직설적이고 논리 강함
        Double flame = (q3 + (6.0 - q5) + (6.0 - q6)) / 3.0 * 2.0;
        axes.put("flame", flame);

        // 이파리형: 공감 중심, 조화 추구
        Double leaf = (q4 + q6) / 2.0 * 2.0;
        axes.put("leaf", leaf);

        // 달빛형: 간접 표현, 암묵적 기대
        Double moon = (q5 + q10) / 2.0 * 2.0;
        axes.put("moon", moon);

        // 별빛형: 구체성 선호, 논리
        Double star = (q3 + q7) / 2.0 * 2.0;
        axes.put("star", star);

        return axes;
    }
}
