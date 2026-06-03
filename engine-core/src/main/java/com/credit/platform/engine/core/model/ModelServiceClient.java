package com.credit.platform.engine.core.model;

/**
 * 模型推理服务客户端接口。
 * <p>
 * 定义调用模型推理服务的统一协议。支持 REST/gRPC 等多种传输方式，
 * 由具体实现类决定通信协议。引擎核心通过此接口与模型服务解耦。
 * </p>
 *
 * <p>设计要点:
 * <ul>
 *   <li>Mock 模式: {@link ModelConfig#isMockEnabled()} 时直接返回配置的 mockScore，不发起网络调用</li>
 *   <li>降级策略: 调用超时或失败时返回降级响应 (score=配置的 mockScore)</li>
 *   <li>线程安全: 实现类必须线程安全</li>
 * </ul>
 * </p>
 *
 * @see DefaultModelServiceClient
 * @see ModelConfig
 */
public interface ModelServiceClient {

    /**
     * 调用模型推理服务。
     *
     * @param request 模型推理请求
     * @return 模型推理响应
     */
    ModelResponse predict(ModelRequest request);

    /**
     * 调用模型推理服务，带超时覆盖。
     *
     * @param request   模型推理请求
     * @param timeoutMs 超时时间 (毫秒)，覆盖默认配置
     * @return 模型推理响应
     */
    ModelResponse predict(ModelRequest request, long timeoutMs);

    /**
     * 健康检查 — 验证模型服务是否可用。
     *
     * @return true 表示服务可用
     */
    boolean isHealthy();
}
