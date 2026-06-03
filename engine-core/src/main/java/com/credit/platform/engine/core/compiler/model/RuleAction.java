package com.credit.platform.engine.core.compiler.model;

import com.credit.platform.engine.common.model.ActionType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 规则命中时执行的动作。
 * <p>
 * 不可变对象，编译时创建，运行时只读。
 * </p>
 *
 * <pre>
 * // 示例：拒绝动作
 * RuleAction action = RuleAction.builder()
 *     .type(ActionType.REJECT)
 *     .reason("年龄不符准入要求")
 *     .code("AGE_001")
 *     .build();
 * </pre>
 */
public final class RuleAction {

    /** 动作类型 */
    private final ActionType type;
    /** 动作原因（用于可解释性） */
    private final String reason;
    /** 动作编码 */
    private final String code;
    /** 额外参数（如额度、利率等） */
    private final Map<String, Object> params;

    private RuleAction(Builder builder) {
        this.type = builder.type;
        this.reason = builder.reason;
        this.code = builder.code;
        this.params = builder.params != null
            ? Collections.unmodifiableMap(new LinkedHashMap<>(builder.params))
            : Collections.emptyMap();
    }

    public ActionType getType() { return type; }
    public String getReason() { return reason; }
    public String getCode() { return code; }
    public Map<String, Object> getParams() { return params; }

    public static Builder builder() { return new Builder(); }

    /** 构建器 */
    public static class Builder {
        private ActionType type;
        private String reason;
        private String code;
        private Map<String, Object> params;

        public Builder type(ActionType type) { this.type = type; return this; }
        public Builder reason(String reason) { this.reason = reason; return this; }
        public Builder code(String code) { this.code = code; return this; }
        public Builder params(Map<String, Object> params) { this.params = params; return this; }
        public RuleAction build() {
            if (type == null) {
                throw new IllegalStateException("Action type must not be null");
            }
            return new RuleAction(this);
        }
    }

    @Override
    public String toString() {
        return "RuleAction{type=" + type + ", code='" + code + "', reason='" + reason + "'}";
    }
}
