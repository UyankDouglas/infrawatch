package com.infrawatch.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StatusCodeMatcherTest {

    @Test
    void matchesRangesAndSingles() {
        StatusCodeMatcher m = new StatusCodeMatcher("200-299, 301,404");
        assertThat(m.matches(200)).isTrue();
        assertThat(m.matches(250)).isTrue();
        assertThat(m.matches(299)).isTrue();
        assertThat(m.matches(301)).isTrue();
        assertThat(m.matches(404)).isTrue();
        assertThat(m.matches(300)).isFalse();
        assertThat(m.matches(500)).isFalse();
    }

    @Test
    void defaultsTo2xx() {
        StatusCodeMatcher m = new StatusCodeMatcher(null);
        assertThat(m.matches(204)).isTrue();
        assertThat(m.matches(302)).isFalse();
    }

    @Test
    void neverAcceptsAllCodesImplicitly() {
        StatusCodeMatcher m = new StatusCodeMatcher("200");
        assertThat(m.matches(503)).isFalse();
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> new StatusCodeMatcher("abc")).isInstanceOf(IllegalArgumentException.class);
    }
}
