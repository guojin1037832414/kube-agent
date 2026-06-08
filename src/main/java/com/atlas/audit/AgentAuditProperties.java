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
        private int retentionDays = 30;
        private long maxFileBytes = 268_435_456L;
        private boolean exportEnabled = false;
        private String exportDirectory = "target/agent-audit/export";
        private String exportFormat = "jsonl-redacted";
        private int queryMaxScanRecords = 10_000;
        private int queryMaxResults = 500;
        private int auditIdMaxPhaseRecords = 20;

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

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }

        public long getMaxFileBytes() {
            return maxFileBytes;
        }

        public void setMaxFileBytes(long maxFileBytes) {
            this.maxFileBytes = maxFileBytes;
        }

        public boolean isExportEnabled() {
            return exportEnabled;
        }

        public void setExportEnabled(boolean exportEnabled) {
            this.exportEnabled = exportEnabled;
        }

        public String getExportDirectory() {
            return exportDirectory;
        }

        public void setExportDirectory(String exportDirectory) {
            this.exportDirectory = exportDirectory;
        }

        public String getExportFormat() {
            return exportFormat;
        }

        public void setExportFormat(String exportFormat) {
            this.exportFormat = exportFormat;
        }

        public int getQueryMaxScanRecords() {
            return queryMaxScanRecords;
        }

        public void setQueryMaxScanRecords(int queryMaxScanRecords) {
            this.queryMaxScanRecords = queryMaxScanRecords;
        }

        public int getQueryMaxResults() {
            return queryMaxResults;
        }

        public void setQueryMaxResults(int queryMaxResults) {
            this.queryMaxResults = queryMaxResults;
        }

        public int getAuditIdMaxPhaseRecords() {
            return auditIdMaxPhaseRecords;
        }

        public void setAuditIdMaxPhaseRecords(int auditIdMaxPhaseRecords) {
            this.auditIdMaxPhaseRecords = auditIdMaxPhaseRecords;
        }
    }
}
