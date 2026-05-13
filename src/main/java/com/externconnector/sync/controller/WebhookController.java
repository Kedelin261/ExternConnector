package com.externconnector.sync.controller;

import com.externconnector.sync.service.ClickUpWebhookService;
import com.externconnector.sync.service.LinearWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Webhook ingestion endpoints.
 * Both endpoints always return 200 OK on signature-valid requests
 * (even if downstream processing fails) except on auth failures (401).
 * This prevents unnecessary retries for logic errors.
 *
 * Exception: on transient errors (API down, DB down), we return 500
 * so the webhook provider will retry.
 */
@RestController
@RequestMapping("/webhooks")
@Tag(name = "Webhooks", description = "Webhook ingestion from Linear and ClickUp")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final LinearWebhookService linearWebhookService;
    private final ClickUpWebhookService clickUpWebhookService;

    public WebhookController(
            LinearWebhookService linearWebhookService,
            ClickUpWebhookService clickUpWebhookService) {
        this.linearWebhookService = linearWebhookService;
        this.clickUpWebhookService = clickUpWebhookService;
    }

    /**
     * POST /webhooks/linear
     * Receives Linear webhook events.
     * Header: X-Linear-Signature: <hmac-sha256>
     */
    @PostMapping("/linear")
    @Operation(summary = "Receive Linear webhook")
    public ResponseEntity<Map<String, String>> receiveLinear(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Linear-Signature", required = false) String signature) {

        log.debug("Received Linear webhook, signature present: {}", signature != null);
        linearWebhookService.handle(rawBody, signature);
        return ResponseEntity.ok(Map.of("status", "accepted"));
    }

    /**
     * POST /webhooks/clickup
     * Receives ClickUp webhook events.
     * Header: X-Signature: <hmac-sha256>
     */
    @PostMapping("/clickup")
    @Operation(summary = "Receive ClickUp webhook")
    public ResponseEntity<Map<String, String>> receiveClickUp(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Signature", required = false) String signature) {

        log.debug("Received ClickUp webhook, signature present: {}", signature != null);
        clickUpWebhookService.handle(rawBody, signature);
        return ResponseEntity.ok(Map.of("status", "accepted"));
    }
}
