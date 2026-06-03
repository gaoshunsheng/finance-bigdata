package com.credit.platform.engine.common.exception;

/**
 * 决策引擎基础异常。
 * <p>
 * 所有决策引擎相关异常的父类，均为运行时异常。
 * </p>
 */
public class DecisionEngineException extends RuntimeException {

    /**
     * 构造带错误信息的异常。
     *
     * @param message 错误信息
     */
    public DecisionEngineException(String message) {
        super(message);
    }

    /**
     * 构造带错误信息和原因的异常。
     *
     * @param message 错误信息
     * @param cause   原因
     */
    public DecisionEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
