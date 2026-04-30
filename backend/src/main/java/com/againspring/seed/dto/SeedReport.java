package com.againspring.seed.dto;

import java.util.List;

/**
 * 시드 리포트 DTO
 * Report 엔티티 정의
 */
public record SeedReport(
    int criticismScore,         // 0~10
    int contemptScore,
    int defensivenessScore,
    int stonewallingScore,
    List<String> repairSuggestions,   // 1~5개
    String nvcObservationA,     // aToB
    String nvcFeelingA,
    String nvcNeedA,
    String nvcRequestA,
    String nvcObservationB,     // bToA (solo면 null)
    String nvcFeelingB,
    String nvcNeedB,
    String nvcRequestB,
    String fourSentenceDraft,   // suggestedApproach에 저장
    Integer ratioA,             // null이면 solo
    Integer ratioB,
    String conflictType         // "DIFFERENCE", "FACTUAL", "MIXED"
) {}
