package com.againspring.aiuser.orchestrator.service.engagement;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EngagementSnapshotReaderVerdictLeanTest {

    @Test
    void leanFromSympathyMapsBands() {
        assertThat(EngagementSnapshotReader.leanFromSympathy(0.9)).isEqualTo("AUTHOR");
        assertThat(EngagementSnapshotReader.leanFromSympathy(0.5)).isEqualTo("SPLIT");
        assertThat(EngagementSnapshotReader.leanFromSympathy(0.2)).isEqualTo("PARTNER");
    }

    @Test
    void leanFromCandidatesJsonReadsProvenance() {
        assertThat(EngagementSnapshotReader.leanFromCandidatesJson(
                "{\"_sourceProvenance\":{\"verdictLean\":\"partner\"}}")).isEqualTo("PARTNER");
        assertThat(EngagementSnapshotReader.leanFromCandidatesJson(
                "{\"_sourceProvenance\":{\"sourceExampleId\":1}}")).isNull();
        assertThat(EngagementSnapshotReader.leanFromCandidatesJson("not json")).isNull();
    }
}
