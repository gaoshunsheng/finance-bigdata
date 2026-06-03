package com.credit.platform.engine.sdk.autoconfigure;

import com.credit.platform.engine.sdk.DecisionClient;
import com.credit.platform.engine.sdk.DecisionClientConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 决策引擎客户端 Spring Boot 自动装配。
 * <p>
 * 当 classpath 中存在 {@link DecisionClient} 类且 {@code decision.client.enabled=true} 时，
 * 自动创建 {@link DecisionClient} Bean 并注入 Spring 容器。
 * </p>
 *
 * <p>使用方式 — 在 Spring Boot 应用的 pom.xml 中引入 decision-sdk 依赖:
 * <pre>
 * &lt;dependency&gt;
 *     &lt;groupId&gt;com.credit.platform&lt;/groupId&gt;
 *     &lt;artifactId&gt;decision-sdk&lt;/artifactId&gt;
 * &lt;/dependency&gt;
 * </pre>
 * </p>
 *
 * <p>然后在代码中直接注入使用:
 * <pre>
 * &#64;Autowired
 * private DecisionClient decisionClient;
 *
 * public void makeDecision() {
 *     DecisionRequest request = new DecisionRequest();
 *     request.setStrategyId("STR_CREDIT_V3");
 *     DecisionResponse response = decisionClient.execute(request);
 * }
 * </pre>
 * </p>
 */
@AutoConfiguration
@ConditionalOnClass(DecisionClient.class)
@ConditionalOnProperty(prefix = "decision.client", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DecisionClientProperties.class)
public class DecisionAutoConfiguration {

    /**
     * 创建决策引擎客户端 Bean。
     *
     * @param properties 配置属性
     * @return DecisionClient 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public DecisionClient decisionClient(DecisionClientProperties properties) {
        DecisionClientConfig config = properties.toConfig();
        return DecisionClient.create(config);
    }
}
