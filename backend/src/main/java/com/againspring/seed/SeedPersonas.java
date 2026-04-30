package com.againspring.seed;

import com.againspring.domain.User;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 시드 페르소나 정의 (10명)
 * 정적 메서드로 User 엔티티 생성
 * test1~6: Phase 1 기본 페르소나
 * test7~10: Phase 2 확장 페르소나 (영희/동현/지영/태우)
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SeedPersonas {

    private static final String PASSWORD = "test123";

    /**
     * 10명의 페르소나 User 엔티티 생성
     */
    public static List<User> build(PasswordEncoder passwordEncoder) {
        return Arrays.asList(
            buildUser("test1@again.com", "서영", "ENFJ", "wave",
                    List.of(4, 3, 2, 4, 4, 4, 3, 4, 3, 4),
                    Map.of("e_i", 30, "s_n", 55, "t_f", 65, "j_p", 40),
                    passwordEncoder),
            buildUser("test2@again.com", "지훈", "ISTJ", "mountain",
                    List.of(2, 1, 4, 2, 2, 3, 2, 3, 2, 2),
                    Map.of("e_i", 70, "s_n", 40, "t_f", 35, "j_p", 25),
                    passwordEncoder),
            buildUser("test3@again.com", "수민", "ENFP", "wave",
                    List.of(5, 4, 1, 5, 5, 3, 4, 5, 3, 5),
                    Map.of("e_i", 25, "s_n", 60, "t_f", 55, "j_p", 35),
                    passwordEncoder),
            buildUser("test4@again.com", "정현", "ESTJ", "flame",
                    List.of(3, 2, 5, 2, 3, 3, 4, 3, 4, 3),
                    Map.of("e_i", 30, "s_n", 40, "t_f", 35, "j_p", 25),
                    passwordEncoder),
            buildUser("test5@again.com", "민수", "INTP", "star",
                    List.of(3, 2, 4, 2, 2, 2, 3, 3, 2, 2),
                    Map.of("e_i", 65, "s_n", 55, "t_f", 35, "j_p", 45),
                    passwordEncoder),
            buildUser("test6@again.com", "다현", "INFP", "leaf",
                    List.of(4, 3, 2, 4, 4, 3, 4, 4, 3, 4),
                    Map.of("e_i", 60, "s_n", 55, "t_f", 70, "j_p", 45),
                    passwordEncoder),
            buildUser("test7@again.com", "영희", "ISFJ", "leaf",
                    List.of(3, 2, 3, 2, 3, 3, 4, 3, 3, 3),
                    Map.of("e_i", 75, "s_n", 30, "t_f", 65, "j_p", 30),
                    passwordEncoder),
            buildUser("test8@again.com", "동현", "ESTP", "flame",
                    List.of(2, 1, 5, 3, 2, 2, 3, 2, 4, 2),
                    Map.of("e_i", 20, "s_n", 35, "t_f", 30, "j_p", 55),
                    passwordEncoder),
            buildUser("test9@again.com", "지영", "INFP", "star",
                    List.of(4, 3, 2, 2, 4, 2, 3, 3, 2, 3),
                    Map.of("e_i", 70, "s_n", 50, "t_f", 75, "j_p", 50),
                    passwordEncoder),
            buildUser("test10@again.com", "태우", "ENTJ", "mountain",
                    List.of(2, 1, 5, 4, 2, 3, 4, 2, 5, 2),
                    Map.of("e_i", 15, "s_n", 45, "t_f", 25, "j_p", 20),
                    passwordEncoder)
        );
    }

    /**
     * 개별 User 생성
     */
    private static User buildUser(String email, String nickname, String mbtiType, String communicationStyle,
                                   List<Integer> onboardingAnswers, Map<String, Integer> mbtiProfile,
                                   PasswordEncoder passwordEncoder) {
        Instant now = Instant.now();
        String userId = UUID.randomUUID().toString().replace("-", "");

        return User.builder()
                .id(userId)
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .nickname(nickname)
                .mbtiType(mbtiType)
                .communicationStyle(communicationStyle)
                .onboardingAnswers(onboardingAnswers)
                .mbtiProfile(mbtiProfile)
                .roles(List.of("USER"))
                .isGuest(false)
                .provider(null)
                .providerId(null)
                .createdAt(now)
                .updatedAt(now)
                .onboardingCompletedAt(now)
                .build();
    }
}
