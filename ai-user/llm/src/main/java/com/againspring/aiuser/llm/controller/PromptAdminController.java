package com.againspring.aiuser.llm.controller;

import com.againspring.aiuser.llm.service.PromptAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 백엔드 admin이 프롬프트 템플릿 저장 후 즉시 반영하기 위한 내부 엔드포인트.
 * 외부 노출 없음 — Docker 내부 네트워크에서만 호출 가능.
 * DB 미사용 — classpath 프롬프트 템플릿을 in-memory로 재로드할 뿐이다(워커는 무상태, 2026-09).
 */
@Slf4j
@RestController
@RequestMapping("/internal/prompts")
@RequiredArgsConstructor
public class PromptAdminController {

    private final PromptAssembler promptAssembler;

    @PostMapping("/reload")
    public ResponseEntity<Map<String, String>> reload() {
        log.info("[prompt-admin] reload triggered by admin");
        promptAssembler.reload();
        return ResponseEntity.ok(Map.of("status", "reloaded"));
    }
}
