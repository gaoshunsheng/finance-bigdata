package com.credit.platform.engine.common.exception;

/**
 * 变量解析异常。
 * <p>
 * 当决策引擎在解析变量（特征值）时失败抛出。
 * </p>
 */
public class VariableResolveException extends DecisionEngineException {

    /**
     * 构造带错误信息的异常。
     *
     * @param message 错误信息
     */
    public VariableResolveException(String message) {
        super(message);
    }

    /**
     * 构造带错误信息和原因的异常。
     *
     * @param message 错误信息
     * @param cause   原因
     */
    public VariableResolveException(String message, Throwable cause) {
        super(message, cause);
    }
}
