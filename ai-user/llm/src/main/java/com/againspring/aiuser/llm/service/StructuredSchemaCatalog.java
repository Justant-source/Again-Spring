package com.againspring.aiuser.llm.service;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * Keeps the two CLI integrations on exactly the same JSON Schema resources.
 * Codex requires a filesystem path, so resources are copied once into a private
 * temporary directory instead of relying on exploded-JAR resources.
 */
@Component
public class StructuredSchemaCatalog {
    private final Map<StructuredOutputSchema, String> json = new EnumMap<>(StructuredOutputSchema.class);
    private final Map<StructuredOutputSchema, Path> codexPaths = new EnumMap<>(StructuredOutputSchema.class);

    @PostConstruct
    void load() {
        try {
            Path directory = Files.createTempDirectory("again-spring-structured-schemas-");
            directory.toFile().deleteOnExit();
            for (StructuredOutputSchema schema : StructuredOutputSchema.values()) {
                String value = new ClassPathResource(schema.classpathLocation()).getContentAsString(StandardCharsets.UTF_8);
                json.put(schema, value);
                Path target = directory.resolve(Path.of(schema.classpathLocation()).getFileName());
                Files.writeString(target, value, StandardCharsets.UTF_8);
                target.toFile().deleteOnExit();
                codexPaths.put(schema, target);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load structured output schemas", e);
        }
    }

    public String json(StructuredOutputSchema schema) {
        return required(json.get(schema), schema);
    }

    public String codexPath(StructuredOutputSchema schema) {
        return required(codexPaths.get(schema) == null ? null : codexPaths.get(schema).toString(), schema);
    }

    private static String required(String value, StructuredOutputSchema schema) {
        if (value == null || value.isBlank()) throw new IllegalStateException("Structured schema is unavailable: " + schema);
        return value;
    }
}
