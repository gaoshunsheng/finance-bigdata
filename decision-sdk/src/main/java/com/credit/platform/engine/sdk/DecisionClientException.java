package com.credit.platform.engine.sdk;

/**
 * 决策客户端异常。
 * <p>
 * 封装决策引擎调用过程中的各类错误，包括网络错误、服务端错误和配置错误。
 * </p>
 */
public class DecisionClientException extends RuntimeException {

    public DecisionClientException(String message) {
        super(message);
    }

    public DecisionClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
