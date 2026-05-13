package com.externconnector.sync.controller;

import com.externconnector.sync.exception.GlobalExceptionHandler;
import com.externconnector.sync.exception.WebhookAuthException;
import com.externconnector.sync.security.WebhookSignatureVerifier;
import com.externconnector.sync.service.ClickUpWebhookService;
import com.externconnector.sync.service.LinearWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WebhookController.class)
@Import(GlobalExceptionHandler.class)
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LinearWebhookService linearWebhookService;

    @MockBean
    private ClickUpWebhookService clickUpWebhookService;

    private static final String LINEAR_PAYLOAD = "{\"action\":\"update\",\"type\":\"Issue\",\"data\":{\"id\":\"issue1\",\"state\":{\"name\":\"Done\"}}}";
    private static final String CLICKUP_PAYLOAD = "{\"event\":\"taskStatusUpdated\",\"task_id\":\"task1\",\"history_items\":[{\"field\":\"status\",\"after\":{\"status\":\"complete\"}}]}";

    // ─── Linear webhook ───────────────────────────────────────────────────────

    @Test
    void postLinearWebhook_validSignature_returns200() throws Exception {
        doNothing().when(linearWebhookService).handle(anyString(), anyString());

        mockMvc.perform(post("/webhooks/linear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Linear-Signature", "validhash")
                        .content(LINEAR_PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void postLinearWebhook_invalidSignature_returns401() throws Exception {
        doThrow(new WebhookAuthException("Invalid Linear webhook signature"))
                .when(linearWebhookService).handle(anyString(), anyString());

        mockMvc.perform(post("/webhooks/linear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Linear-Signature", "badsig")
                        .content(LINEAR_PAYLOAD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postLinearWebhook_missingSignature_returns401() throws Exception {
        doThrow(new WebhookAuthException("Missing X-Linear-Signature header"))
                .when(linearWebhookService).handle(anyString(), isNull());

        mockMvc.perform(post("/webhooks/linear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LINEAR_PAYLOAD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postLinearWebhook_emptyBody_returns200() throws Exception {
        doNothing().when(linearWebhookService).handle(anyString(), anyString());

        mockMvc.perform(post("/webhooks/linear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Linear-Signature", "sig")
                        .content("{}"))
                .andExpect(status().isOk());
    }

    // ─── ClickUp webhook ──────────────────────────────────────────────────────

    @Test
    void postClickUpWebhook_validSignature_returns200() throws Exception {
        doNothing().when(clickUpWebhookService).handle(anyString(), anyString());

        mockMvc.perform(post("/webhooks/clickup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", "validhash")
                        .content(CLICKUP_PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void postClickUpWebhook_invalidSignature_returns401() throws Exception {
        doThrow(new WebhookAuthException("Invalid ClickUp webhook signature"))
                .when(clickUpWebhookService).handle(anyString(), anyString());

        mockMvc.perform(post("/webhooks/clickup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", "badsig")
                        .content(CLICKUP_PAYLOAD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postClickUpWebhook_malformedJson_returns200() throws Exception {
        // Malformed JSON should be handled gracefully (no exception thrown to caller)
        doNothing().when(clickUpWebhookService).handle(anyString(), anyString());

        mockMvc.perform(post("/webhooks/clickup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", "sig")
                        .content("{not valid json"))
                .andExpect(status().isOk());
    }

    @Test
    void postLinearWebhook_serviceThrowsRuntimeException_returns500() throws Exception {
        doThrow(new RuntimeException("DB connection failed"))
                .when(linearWebhookService).handle(anyString(), anyString());

        mockMvc.perform(post("/webhooks/linear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Linear-Signature", "sig")
                        .content(LINEAR_PAYLOAD))
                .andExpect(status().isInternalServerError());
    }
}
