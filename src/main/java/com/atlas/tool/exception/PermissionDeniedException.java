package com.atlas.tool.exception;

/**
 * Tool 权限拒绝异常 — 当用户试图调用越权 Tool 时抛出。
 *
 * @author Atlas Team
 * @since 3.1.0
 */
public class PermissionDeniedException extends RuntimeException {

    private final String deniedTool;
    private final String requiredRole;
    private final String currentRole;

    public PermissionDeniedException(String message, String deniedTool,
                                      String requiredRole, String currentRole) {
        super(message);
        this.deniedTool = deniedTool;
        this.requiredRole = requiredRole;
        this.currentRole = currentRole;
    }

    public String getDeniedTool() { return deniedTool; }
    public String getRequiredRole() { return requiredRole; }
    public String getCurrentRole() { return currentRole; }
}
