package com.againspring.aiuser.orchestrator.engine;

import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.repository.PersonaActionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * 이번 tick 행동 페르소나 선택.
 * 가중치: tier × 시간대 circadian × 쿨다운 감쇠.
 * LLM 미사용.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersonaSelector {

    private final PersonaActionLogRepository actionLogRepo;
    private static final Random RNG = new Random();

    /** Minimum cooldown between actions per persona (in minutes). */
    private static final int MIN_COOLDOWN_MIN = 20;
    private static final int MAX_COOLDOWN_MIN = 90;

    /**
     * Pick one persona from candidates, weighted by tier × circadian × cooldown decay.
     * @param candidates active personas (pre-filtered)
     * @param hour       current KST hour (0-23)
     */
    public Optional<Persona> pick(List<Persona> candidates, int hour) {
        if (candidates.isEmpty()) return Optional.empty();

        // Score each candidate
        double[] scores = new double[candidates.size()];
        double totalScore = 0;
        for (int i = 0; i < candidates.size(); i++) {
            Persona p = candidates.get(i);
            double tierW = tierWeight(p.getTier());
            double circadianW = circadianWeight(p, hour);
            double cooldownW = cooldownWeight(p, hour);
            scores[i] = tierW * circadianW * cooldownW;
            totalScore += scores[i];
        }

        if (totalScore <= 0) {
            // All on cooldown — return random one
            return Optional.of(candidates.get(RNG.nextInt(candidates.size())));
        }

        // Weighted random selection
        double rand = RNG.nextDouble() * totalScore;
        double cum = 0;
        for (int i = 0; i < candidates.size(); i++) {
            cum += scores[i];
            if (rand <= cum) return Optional.of(candidates.get(i));
        }
        return Optional.of(candidates.get(candidates.size() - 1));
    }

    public boolean isOnCooldown(Persona persona) {
        Instant cutoff = Instant.now().minusSeconds(MIN_COOLDOWN_MIN * 60L);
        return actionLogRepo.findTopByPersonaIdOrderByCreatedAtDesc(persona.getId())
            .map(log -> log.getCreatedAt().isAfter(cutoff))
            .orElse(false);
    }

    private double tierWeight(String tier) {
        if (tier == null) return 1.0;
        return switch (tier) {
            case "HEAVY" -> 3.0;
            case "REGULAR" -> 2.0;
            case "LIGHT" -> 1.0;
            default -> 0.0; // DORMANT
        };
    }

    private double circadianWeight(Persona persona, int hour) {
        List<Double> curve = persona.getCircadian();
        if (curve == null || curve.size() < 24) return 0.5;
        return Math.max(0.0, Math.min(1.0, curve.get(hour)));
    }

    private double cooldownWeight(Persona persona, int hour) {
        return actionLogRepo.findTopByPersonaIdOrderByCreatedAtDesc(persona.getId())
            .map(log -> {
                long minutesSince = (Instant.now().getEpochSecond() - log.getCreatedAt().getEpochSecond()) / 60;
                if (minutesSince < MIN_COOLDOWN_MIN) return 0.0;
                double base = minutesSince >= MAX_COOLDOWN_MIN ? 1.0 :
                    (double)(minutesSince - MIN_COOLDOWN_MIN) / (MAX_COOLDOWN_MIN - MIN_COOLDOWN_MIN);
                // circadian 가중치 적용 (time-of-day 보정)
                double circadianMult = circadianWeight(persona, hour);
                return Math.min(1.0, base * circadianMult);
            })
            .orElse(1.0); // no history = fresh, full weight
    }
}
