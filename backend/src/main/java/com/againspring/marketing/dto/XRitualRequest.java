package com.againspring.marketing.dto;

/**
 * ASM {@code POST /api/v1/x/ritual} body. {@code slot} is {@code morning} or {@code night}.
 */
public record XRitualRequest(String slot, String text) {}
