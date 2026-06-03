package com.credit.platform.engine.core.cache;

/**
 * 编译产物提供器 — 用于热加载时重新编译规则。
 * <p>
 * 函数式接口，由上层模块提供具体的编译逻辑:
 * <ul>
 *   <li>从数据库加载最新 JSON</li>
 *   <li>调用对应的 Compiler 编译</li>
 *   <li>返回编译后的产物</li>
 * </ul>
 * engine-core 不引入数据库依赖，仅定义接口。
 * </p>
 *
 * @param <T> 编译产物类型
 */
@FunctionalInterface
public interface ArtifactCompiler<T> {

    /**
     * 重新编译指定产物。
     *
     * @param artifactId 产物 ID
     * @param version    目标版本号
     * @return 编译后的产物
     * @throws Exception 编译失败时抛出异常
     */
    T compile(String artifactId, int version) throws Exception;
}
