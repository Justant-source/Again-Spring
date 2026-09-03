package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.exception.ClaudeCodeException;
import com.againspring.aiuser.llm.exception.LlmException;
import com.againspring.aiuser.llm.pool.CancelableInvocation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * LLM을 호출하지 않는 record-replay provider. dev canary·단위 테스트용.
 * 픽스처: classpath {@code stub/<name>} 기본, {@code LLM_STUB_FIXTURE_DIR}/<name> 우선.
 * 네트워크·프로세스 스폰 없음 — 순수 파일 읽기만 수행한다.
 */
@Slf4j
@Service
public class StubInvoker implements Invoker {

    private final String fixtureDir;

    public StubInvoker(@Value("${llm.stub.fixture-dir:}") String fixtureDir) {
        this.fixtureDir = fixtureDir == null ? "" : fixtureDir.trim();
    }

    @Override
    public String invoke(String prompt, String model) throws LlmException {
        return read("plain.txt");
    }

    @Override
    public String invokeSingleAttempt(String prompt, String model, StructuredOutputSchema schema) throws LlmException {
        return substitute(read(schemaFileBase(schema) + ".json"), prompt);
    }

    @Override
    public String invokeWithCancelSupport(String prompt, String model, CancelableInvocation inv) {
        String out = read("plain.txt");
        inv.updatePartial(out);
        return out;
    }

    /**
     * {@code StructuredSchemaCatalog}가 로드하는 classpath 파일명
     * ({@code StructuredOutputSchema#classpathLocation()} 기저명, 예:
     * {@code schemas/thread-plan.schema.json} → {@code thread-plan})과 동일한 값을
     * 반환한다. 실제 매핑 근거는 enum 상수명을 케밥케이스로 변환한 값이 그 기저명과
     * 일치한다는 사실이다 (THREAD_PLAN → thread-plan, PAIRED_PHASE1 → paired-phase1 등) —
     * {@code StructuredSchemaCatalog}에는 별도의 이름-매핑 메서드가 없어, enum 상수명 기반
     * 파생을 사용한다.
     */
    static String schemaFileBase(StructuredOutputSchema schema) {
        return schema.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static final java.util.regex.Pattern HEX32 = java.util.regex.Pattern.compile("(?<![0-9a-f_])[0-9a-f]{32}(?![0-9a-f_])");
    private static final java.util.regex.Pattern PLACEHOLDER = java.util.regex.Pattern.compile("__PERSONA_(\\d+)__");

    /** 프롬프트에 등장한 32-hex persona id를 순서대로 __PERSONA_n__에 대입. 부족한 자리의 comment 객체는 제거. */
    static String substitute(String fixture, String prompt) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        java.util.regex.Matcher m = HEX32.matcher(prompt == null ? "" : prompt);
        while (m.find()) if (!ids.contains(m.group())) ids.add(m.group());
        if (ids.isEmpty()) return fixture;
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = om.readTree(fixture);
            com.fasterxml.jackson.databind.JsonNode comments = root.get("comments");
            if (comments != null && comments.isArray()) {
                java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = comments.iterator();
                while (it.hasNext()) {
                    com.fasterxml.jackson.databind.JsonNode c = it.next();
                    java.util.regex.Matcher pm = PLACEHOLDER.matcher(c.path("personaId").asText(""));
                    if (pm.matches() && Integer.parseInt(pm.group(1)) > ids.size()) it.remove();
                }
            }
            String json = om.writeValueAsString(root);
            for (int i = 0; i < ids.size(); i++) json = json.replace("__PERSONA_" + (i + 1) + "__", ids.get(i));
            return json;
        } catch (Exception e) {
            return fixture;
        }
    }

    private String read(String name) {
        try {
            if (!fixtureDir.isBlank()) {
                Path p = Path.of(fixtureDir, name).normalize();
                Path base = Path.of(fixtureDir).normalize();
                if (p.startsWith(base) && Files.isRegularFile(p)) {
                    return Files.readString(p, StandardCharsets.UTF_8);
                }
            }
            return new ClassPathResource("stub/" + name).getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ClaudeCodeException("STUB_ERROR", "stub fixture missing: " + name, -1, null);
        }
    }
}
