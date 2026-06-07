package com.againspring.repository.ai;

import com.againspring.domain.ai.AiPromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiPromptTemplateRepository extends JpaRepository<AiPromptTemplate, String> {
    List<AiPromptTemplate> findAllByOrderByKeyAsc();
}
