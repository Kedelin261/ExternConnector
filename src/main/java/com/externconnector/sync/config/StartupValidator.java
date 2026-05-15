package com.externconnector.sync.config;

import com.externconnector.sync.client.ClickUpApiClient;
import com.externconnector.sync.client.LinearApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates all external connectivity after application startup.
 * Logs clear diagnostics — never silently ignores missing config.
 */
@Component
public class StartupValidator {

    private static final Logger log = LoggerFactory.getLogger(StartupValidator.class);

    private final LinearApiClient linearApiClient;
    private final ClickUpApiClient clickUpApiClient;
    private final JdbcTemplate jdbcTemplate;

    @Value("${linear.api.key}")
    private String linearApiKey;

    @Value("${linear.team.id}")
    private String linearTeamId;

    @Value("${linear.webhook.secret}")
    private String linearWebhookSecret;

    @Value("${clickup.api.key}")
    private String clickupApiKey;

    @Value("${clickup.workspace.id}")
    private String clickupWorkspaceId;

    @Value("${clickup.list.id}")
    private String clickupListId;

    @Value("${clickup.webhook.secret}")
    private String clickupWebhookSecret;

    public StartupValidator(
            LinearApiClient linearApiClient,
            ClickUpApiClient clickUpApiClient,
            JdbcTemplate jdbcTemplate) {
        this.linearApiClient = linearApiClient;
        this.clickUpApiClient = clickUpApiClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        log.info("╔══════════════════════════════════════════════╗");
        log.info("║      ExternConnector — Startup Validation    ║");
        log.info("╚══════════════════════════════════════════════╝");

        List<String> failures = new ArrayList<>();

        // ── 1. Env-var presence ────────────────────────────────────────────────
        checkEnvVar("LINEAR_API_KEY / linear.api.key",        linearApiKey,        failures);
        checkEnvVar("LINEAR_TEAM_ID / linear.team.id",        linearTeamId,        failures);
        checkEnvVar("LINEAR_WEBHOOK_SECRET",                   linearWebhookSecret, failures);
        checkEnvVar("CLICKUP_API_KEY / clickup.api.key",      clickupApiKey,       failures);
        checkEnvVar("CLICKUP_WORKSPACE_ID",                   clickupWorkspaceId,  failures);
        checkEnvVar("CLICKUP_LIST_ID / clickup.list.id",      clickupListId,       failures);
        checkEnvVar("CLICKUP_WEBHOOK_SECRET",                  clickupWebhookSecret,failures);

        // ── 2. Database connectivity ───────────────────────────────────────────
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            log.info("  [✓] Database — connected");
        } catch (Exception e) {
            String msg = "Database connectivity FAILED: " + e.getMessage();
            log.error("  [✗] {}", msg);
            failures.add(msg);
        }

        // ── 3. Linear API ──────────────────────────────────────────────────────
        try {
            Map<String, String> states = linearApiClient.getWorkflowStates(linearTeamId);
            log.info("  [✓] Linear API — reachable ({} workflow states for team {})",
                    states.size(), linearTeamId);
            if (states.isEmpty()) {
                log.warn("  [!] Linear API returned 0 workflow states for team {}. " +
                         "Check LINEAR_TEAM_ID is correct.", linearTeamId);
            }
        } catch (Exception e) {
            String msg = "Linear API FAILED: " + e.getMessage();
            log.error("  [✗] {}", msg);
            failures.add(msg);
        }

        // ── 4. ClickUp API ────────────────────────────────────────────────────
        try {
            List<String> statuses = clickUpApiClient.getListStatuses(clickupListId);
            log.info("  [✓] ClickUp API — reachable ({} statuses for list {})",
                    statuses.size(), clickupListId);
            if (statuses.isEmpty()) {
                log.warn("  [!] ClickUp API returned 0 statuses for list {}. " +
                         "Check CLICKUP_LIST_ID is correct.", clickupListId);
            }
        } catch (Exception e) {
            String msg = "ClickUp API FAILED: " + e.getMessage();
            log.error("  [✗] {}", msg);
            failures.add(msg);
        }

        // ── Summary ────────────────────────────────────────────────────────────
        if (failures.isEmpty()) {
            log.info("╔══════════════════════════════════════════════╗");
            log.info("║  All startup checks PASSED ✓                 ║");
            log.info("╚══════════════════════════════════════════════╝");
        } else {
            log.error("╔══════════════════════════════════════════════╗");
            log.error("║  STARTUP VALIDATION FAILED ({} issue(s))    ║", failures.size());
            failures.forEach(f -> log.error("  → {}", f));
            log.error("╚══════════════════════════════════════════════╝");
            // Log clearly but don't hard-crash — let the app handle graceful degradation
            // Remove the throw below if you want the app to start despite API failures
            // throw new IllegalStateException("Startup validation failed: " + failures);
        }
    }

    private void checkEnvVar(String name, String value, List<String> failures) {
        boolean present = value != null && !value.isBlank() && !value.startsWith("placeholder");
        if (present) {
            log.info("  [✓] {} — set ({}...)", name,
                    value.length() > 8 ? value.substring(0, 8) : "****");
        } else {
            String msg = "Missing or placeholder env var: " + name;
            log.error("  [✗] {}", msg);
            failures.add(msg);
        }
    }
}
