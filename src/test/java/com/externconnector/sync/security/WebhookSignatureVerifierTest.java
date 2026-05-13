package com.externconnector.sync.security;

import com.externconnector.sync.exception.WebhookAuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class WebhookSignatureVerifierTest {

    private WebhookSignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new WebhookSignatureVerifier();
    }

    @Test
    void verifyLinear_validSignature_passes() {
        String secret = "mysecret";
        String body = "{\"action\":\"update\",\"type\":\"Issue\"}";
        String sig = verifier.computeHmac(body, secret);
        assertThatNoException().isThrownBy(() -> verifier.verifyLinear(body, sig, secret));
    }

    @Test
    void verifyLinear_invalidSignature_throws() {
        assertThatThrownBy(() -> verifier.verifyLinear("{}", "badsig", "secret"))
                .isInstanceOf(WebhookAuthException.class)
                .hasMessageContaining("Invalid Linear webhook signature");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void verifyLinear_missingSignature_throws(String sig) {
        assertThatThrownBy(() -> verifier.verifyLinear("{}", sig, "secret"))
                .isInstanceOf(WebhookAuthException.class)
                .hasMessageContaining("Missing X-Linear-Signature");
    }

    @Test
    void verifyClickUp_validSignature_passes() {
        String secret = "clickupsecret";
        String body = "{\"event\":\"taskStatusUpdated\",\"task_id\":\"abc123\"}";
        String sig = verifier.computeHmac(body, secret);
        assertThatNoException().isThrownBy(() -> verifier.verifyClickUp(body, sig, secret));
    }

    @Test
    void verifyClickUp_invalidSignature_throws() {
        assertThatThrownBy(() -> verifier.verifyClickUp("{}", "wrongsig", "secret"))
                .isInstanceOf(WebhookAuthException.class)
                .hasMessageContaining("Invalid ClickUp webhook signature");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void verifyClickUp_missingSignature_throws(String sig) {
        assertThatThrownBy(() -> verifier.verifyClickUp("{}", sig, "secret"))
                .isInstanceOf(WebhookAuthException.class)
                .hasMessageContaining("Missing X-Signature");
    }

    @Test
    void computeHmac_isConsistent() {
        String body = "test body";
        String secret = "topsecret";
        String h1 = verifier.computeHmac(body, secret);
        String h2 = verifier.computeHmac(body, secret);
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    void computeHmac_differentBodies_differentResults() {
        String secret = "secret";
        String h1 = verifier.computeHmac("body1", secret);
        String h2 = verifier.computeHmac("body2", secret);
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void computeHmac_differentSecrets_differentResults() {
        String body = "same body";
        String h1 = verifier.computeHmac(body, "secret1");
        String h2 = verifier.computeHmac(body, "secret2");
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void verifyLinear_caseInsensitiveSignature_passes() {
        String secret = "mysecret";
        String body = "{\"action\":\"update\"}";
        String sig = verifier.computeHmac(body, secret).toUpperCase();
        // Should still pass since we normalize to lowercase
        assertThatNoException().isThrownBy(() -> verifier.verifyLinear(body, sig, secret));
    }

    @Test
    void replayAttack_differentBody_fails() {
        String secret = "secret";
        String originalBody = "{\"action\":\"update\",\"data\":{\"id\":\"issue1\"}}";
        String modifiedBody = "{\"action\":\"update\",\"data\":{\"id\":\"issue2\"}}";
        String sig = verifier.computeHmac(originalBody, secret);
        assertThatThrownBy(() -> verifier.verifyLinear(modifiedBody, sig, secret))
                .isInstanceOf(WebhookAuthException.class);
    }
}
