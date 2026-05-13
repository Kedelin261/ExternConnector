package com.externconnector.sync.security;

import com.externconnector.sync.exception.WebhookAuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Timing-safe HMAC-SHA256 webhook signature verification.
 * Prevents timing attacks via MessageDigest.isEqual for all comparisons.
 * Both Linear and ClickUp use HMAC-SHA256 over the raw payload body.
 */
@Component
public class WebhookSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureVerifier.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * Verify Linear webhook signature.
     * Linear sends: X-Linear-Signature: <hex(HMAC-SHA256(secret, body))>
     */
    public void verifyLinear(String rawBody, String receivedSignature, String secret) {
        if (receivedSignature == null || receivedSignature.isBlank()) {
            throw new WebhookAuthException("Missing X-Linear-Signature header");
        }
        String expected = computeHmac(rawBody, secret);
        if (!timingSafeEquals(expected, receivedSignature.toLowerCase())) {
            log.warn("Linear webhook signature mismatch");
            throw new WebhookAuthException("Invalid Linear webhook signature");
        }
    }

    /**
     * Verify ClickUp webhook signature.
     * ClickUp sends: X-Signature: <hex(HMAC-SHA256(secret, body))>
     */
    public void verifyClickUp(String rawBody, String receivedSignature, String secret) {
        if (receivedSignature == null || receivedSignature.isBlank()) {
            throw new WebhookAuthException("Missing X-Signature header");
        }
        String expected = computeHmac(rawBody, secret);
        if (!timingSafeEquals(expected, receivedSignature.toLowerCase())) {
            log.warn("ClickUp webhook signature mismatch");
            throw new WebhookAuthException("Invalid ClickUp webhook signature");
        }
    }

    /**
     * Compute HMAC-SHA256 over the payload using the provided secret.
     * Returns lowercase hex-encoded digest.
     */
    public String computeHmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );
            mac.init(keySpec);
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }

    /**
     * Constant-time comparison to prevent timing attacks.
     * Both strings are converted to bytes and compared via MessageDigest.isEqual.
     */
    private boolean timingSafeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }
}
