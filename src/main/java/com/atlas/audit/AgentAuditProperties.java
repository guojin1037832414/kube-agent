package com.atlas.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent audit configuration.
 *
 * <p>M5.30 keeps durable audit as an explicit opt-in so local development stays
 * simple. Production can enable JSONL first, then replace the sink with a real
 * database or security log backend under the same interface.</p>
 */
@ConfigurationProperties(prefix = "atlas.audit")
public class AgentAuditProperties {

    private final Durable durable = new Durable();

    public Durable getDurable() {
        return durable;
    }

    public static class Durable {
        private boolean enabled = false;
        private boolean failClosedForHighRisk = false;
        private String path = "target/agent-audit/agent-audit.jsonl";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isFailClosedForHighRisk() {
            return failClosedForHighRisk;
        }

        public void setFailClosedForHighRisk(boolean failClosedForHighRisk) {
            this.failClosedForHighRisk = failClosedForHighRisk;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }
}
