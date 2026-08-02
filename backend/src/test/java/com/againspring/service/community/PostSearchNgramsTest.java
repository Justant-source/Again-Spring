package com.againspring.service.community;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PostSearchNgrams — 바이그램 추출")
class PostSearchNgramsTest {

    @Test
    void extractForPost_titleAndBodyBigrams() {
        Set<String> grams = PostSearchNgrams.extractForPost("시댁 갈등", "야근이 너무 많아요");
        assertThat(grams).contains("시댁", "갈등", "야근", "근이");
    }

    @Test
    void extractForQuery_andCaps() {
        List<String> grams = PostSearchNgrams.extractForQuery("시댁갈등");
        assertThat(grams).containsExactly("시댁", "댁갈", "갈등");
    }

    @Test
    void extractForQuery_twoCharToken() {
        assertThat(PostSearchNgrams.extractForQuery("시댁")).containsExactly("시댁");
    }

    @Test
    void skipsSingleCodePointTokens() {
        assertThat(PostSearchNgrams.extractForQuery("가 나")).isEmpty();
    }
}
