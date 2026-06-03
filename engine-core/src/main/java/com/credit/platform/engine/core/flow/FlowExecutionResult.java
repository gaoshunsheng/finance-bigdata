package com.credit.platform.engine.core.flow;

import com.credit.platform.engine.common.model.ActionType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DAG 决策流执行结果。
 */
public final class FlowExecutionResult {

    private final String flowId;
    private final boolean success;
    private final ActionType finalAction;
    private final String finalReason;
    private final List<String> decisionPath;
    private final Map<String, Object> nodeOutputs;
    private final long durationMs;

    private FlowExecutionResult(Builder builder) {
        this.flowId = builder.flowId;
        this.success = builder.success;
        this.finalAction = builder.finalAction;
        this.finalReason = builder.finalReason;
        this.decisionPath = Collections.unmodifiableList(new ArrayList<>(builder.decisionPath));
        this.nodeOutputs = Collections.unmodifiableMap(new LinkedHashMap<>(builder.nodeOutputs));
        this.durationMs = builder.durationMs;
    }

    public String getFlowId() { return flowId; }
    public boolean isSuccess() { return success; }
    public ActionType getFinalAction() { return finalAction; }
    public String getFinalReason() { return finalReason; }
    public List<String> getDecisionPath() { return decisionPath; }
    public Map<String, Object> getNodeOutputs() { return nodeOutputs; }
    public long getDurationMs() { return durationMs; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String flowId;
        private boolean success;
        private ActionType finalAction;
        private String finalReason;
        private List<String> decisionPath = new ArrayList<>();
        private Map<String, Object> nodeOutputs = new LinkedHashMap<>();
        private long durationMs;

        public Builder flowId(String id) { this.flowId = id; return this; }
        public Builder success(boolean s) { this.success = s; return this; }
        public Builder finalAction(ActionType a) { this.finalAction = a; return this; }
        public Builder finalReason(String r) { this.finalReason = r; return this; }
        public Builder decisionPath(List<String> p) { this.decisionPath = p; return this; }
        public Builder nodeOutputs(Map<String, Object> o) { this.nodeOutputs = o; return this; }
        public Builder durationMs(long ms) { this.durationMs = ms; return this; }
        public FlowExecutionResult build() { return new FlowExecutionResult(this); }
    }

    @Override
    public String toString() {
        return "FlowResult{id='" + flowId + "', action=" + finalAction
            + ", path=" + decisionPath + ", duration=" + durationMs + "ms}";
    }
}
