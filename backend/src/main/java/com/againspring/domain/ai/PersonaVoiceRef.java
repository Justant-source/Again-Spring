package com.againspring.domain.ai;

import jakarta.persistence.*;
import lombok.*;

/**
 * personas 테이블의 id·voice_profile만 매핑하는 경량 엔티티.
 * backend에는 JsonType 의존성이 없으므로 String으로 받아 Jackson 수동 파싱.
 * voice_profile의 correction_cautions 키 갱신 전용.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "personas")
public class PersonaVoiceRef {

    @Id
    @Column(length = 32)
    private String id;

    /**
     * personas.voice_profile JSON 전체를 String으로 매핑.
     * orchestrator가 JsonType으로 별도 관리하는 컬럼과 동일.
     * 읽기·쓰기 시 UTF-8/ensure_ascii=False 일관 유지.
     */
    @Column(name = "voice_profile", columnDefinition = "JSON")
    private String voiceProfile;
}
