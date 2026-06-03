package com.credit.platform.engine.common.exception;

/**
 * 规则编译异常。
 * <p>
 * 当规则表达式或规则配置编译失败时抛出。
 * </p>
 */
public class RuleCompileException extends DecisionEngineException {

    /**
     * 构造带错误信息的异常。
     *
     * @param message 错误信息
     */
    public RuleCompileException(String message) {
        super(message);
    }

    /**
     * 构造带错误信息和原因的异常。
     *
     * @param message 错误信息
     * @param cause   原因
     */
    public RuleCompileException(String message, Throwable cause) {
        super(message, cause);
    }
}
