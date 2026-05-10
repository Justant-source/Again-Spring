package com.againspring.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 관리자 대시보드 시스템 헬스 응답 DTO.
 * components 키: backend, database, smtp, anthropic
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SystemHealthResponse {
    private Instant checkedAt;
    private Map<String, ComponentHealth> components;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ComponentHealth {
        private String status;          // OK | WARN | ERROR
        private String message;         // 짧은 사람-가독 설명 (선택)
        private Map<String, Object> details;  // 자유로운 추가 메트릭

        public static ComponentHealth ok(Map<String, Object> details) {
            return ComponentHealth.builder().status("OK").details(details).build();
        }

        public static ComponentHealth warn(String message, Map<String, Object> details) {
            return ComponentHealth.builder().status("WARN").message(message).details(details).build();
        }

        public static ComponentHealth error(String message, Map<String, Object> details) {
            Map<String, Object> safeDetails = details != null ? details : new LinkedHashMap<>();
            return ComponentHealth.builder().status("ERROR").message(message).details(safeDetails).build();
        }
    }
}
