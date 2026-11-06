package com.infrawatch.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AgentTokensTest {

    @Test
    void generatesPrefixedTokenAndStableHash() {
        AgentTokens.Generated g = AgentTokens.generate();
        assertThat(g.token()).startsWith("iwa_").contains(".");
        assertThat(g.prefix()).hasSize(8);
        assertThat(AgentTokens.prefixOf(g.token())).contains(g.prefix());
        assertThat(g.hash()).hasSize(64).isEqualTo(AgentTokens.hash(g.token()));
        assertThat(AgentTokens.matches(g.token(), g.hash())).isTrue();
        assertThat(AgentTokens.matches(g.token() + "x", g.hash())).isFalse();
    }

    @Test
    void tokensAreUnique() {
        assertThat(AgentTokens.generate().token()).isNotEqualTo(AgentTokens.generate().token());
    }

    @Test
    void rejectsMalformedTokens() {
        assertThat(AgentTokens.prefixOf(null)).isEmpty();
        assertThat(AgentTokens.prefixOf("abc")).isEmpty();
        assertThat(AgentTokens.prefixOf("iwa_semponto")).isEmpty();
        assertThat(AgentTokens.prefixOf("iwa_.segredo")).isEmpty();
        assertThatThrownBy(() -> AgentTokens.fromRaw("token-invalido")).isInstanceOf(IllegalArgumentException.class);
    }
}
