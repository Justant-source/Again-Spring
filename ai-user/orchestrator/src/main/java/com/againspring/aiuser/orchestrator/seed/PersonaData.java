package com.againspring.aiuser.orchestrator.seed;

import lombok.*;
import java.util.*;

/** Matches the structure in roster.yml */
@Getter @Setter @NoArgsConstructor
public class PersonaData {
    private String id;
    private String email;
    private String nickname;
    private String ageBand;
    private String gender;
    private String region;
    private String job;
    private String tier;
    private String voice;
    private double slangLevel;
    private List<String> archetypePreferences;
    private Map<String, Double> interests;
    private Map<String, Double> biasProfile;
    private List<Double> circadian;
    private VoiceProfileData voiceProfile;

    @Getter @Setter @NoArgsConstructor
    public static class VoiceProfileData {
        private String description;
        private String tone;
        private String preferredEmojiDensity;
    }
}
