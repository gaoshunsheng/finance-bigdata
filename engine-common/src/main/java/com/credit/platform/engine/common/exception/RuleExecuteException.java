package com.credit.platform.engine.common.exception;

/**
 * 规则执行异常。
 * <p>
 * 当规则在运行时执行失败时抛出。
 * </p>
 */
public class RuleExecuteException extends DecisionEngineException {

    /**
     * 构造带错误信息的异常。
     *
     * @param message 错误信息
     */
    public RuleExecuteException(String message) {
        super(message);
    }

    /**
     * 构造带错误信息和原因的异常。
     *
     * @param message 错误信息
     * @param cause   原因
     */
    public RuleExecuteException(String message, Throwable cause) {
        super(message, cause);
    }
}
