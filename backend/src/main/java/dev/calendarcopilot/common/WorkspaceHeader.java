package dev.calendarcopilot.common;

public final class WorkspaceHeader {

    /** Every data request carries the caller's workspace id in this header. */
    public static final String NAME = "X-Workspace-Id";

    private WorkspaceHeader() {
    }
}
