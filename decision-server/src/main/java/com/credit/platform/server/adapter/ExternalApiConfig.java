package com.credit.platform.server.adapter;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.credit.platform.engine.core.variable.VariableEngine;
import com.credit.platform.engine.core.variable.VariableLayer;
import com.credit.platform.engine.core.variable.VariableProvider;
import com.credit.platform.engine.core.variable.VariableRegistry;
import com.credit.platform.server.adapter.impl.BlacklistMockAdapter;
import com.credit.platform.server.adapter.impl.BusinessRegistrationMockAdapter;
import com.credit.platform.server.adapter.impl.CreditBureauMockAdapter;
import com.credit.platform.server.adapter.impl.JudicialMockAdapter;
import com.credit.platform.server.adapter.impl.TaxSocialMockAdapter;
import com.credit.platform.server.adapter.impl.TelecomMockAdapter;
import com.credit.platform.server.provider.ExternalApiProvider;

/**
 * 外部 API 适配器 Spring 配置 — 注册所有适配器并注入 VariableEngine。
 * <p>
 * 完成以下装配：
 * <ol>
 *   <li>创建 {@link AdapterRegistry} Bean</li>
 *   <li>注册 6 个 Mock 适配器</li>
 *   <li>创建 {@link ExternalApiProvider} (L1 VariableProvider)</li>
 *   <li>装配 {@link VariableEngine} 并注册各层 Provider</li>
 * </ol>
 * </p>
 */
@Configuration
public class ExternalApiConfig {

    private static final Logger log = LoggerFactory.getLogger(ExternalApiConfig.class);

    /**
     * 变量注册表 — 管理所有变量定义。
     * <p>
     * 生产环境中变量定义从 MySQL decision_admin 数据库加载，
     * 此处创建空注册表，后续通过热加载机制填充。
     * </p>
     */
    @Bean
    public VariableRegistry variableRegistry() {
        VariableRegistry registry = new VariableRegistry();

        // 注册外部 API 变量定义（与适配器对应）
        registerExternalVariables(registry);

        log.info("VariableRegistry initialized with {} definitions", registry.size());
        return registry;
    }

    private void registerExternalVariables(VariableRegistry registry) {
        // 征信变量
        registerVar(registry, "credit_score", "征信评分", VariableLayer.EXTERNAL, "INTEGER", "征信");
        registerVar(registry, "overdue_count_6m", "近6月逾期次数", VariableLayer.EXTERNAL, "INTEGER", "征信");
        registerVar(registry, "credit_query_count_3m", "近3月征信查询次数", VariableLayer.EXTERNAL, "INTEGER", "征信");
        registerVar(registry, "max_overdue_days", "最大逾期天数", VariableLayer.EXTERNAL, "INTEGER", "征信");
        registerVar(registry, "loan_count", "贷款笔数", VariableLayer.EXTERNAL, "INTEGER", "征信");
        registerVar(registry, "credit_card_count", "信用卡数量", VariableLayer.EXTERNAL, "INTEGER", "征信");

        // 工商变量
        registerVar(registry, "enterprise_status", "企业经营状态", VariableLayer.EXTERNAL, "STRING", "工商");
        registerVar(registry, "registered_capital", "注册资本", VariableLayer.EXTERNAL, "DECIMAL", "工商");
        registerVar(registry, "established_years", "成立年限", VariableLayer.EXTERNAL, "INTEGER", "工商");
        registerVar(registry, "business_scope_match", "经营范围匹配", VariableLayer.EXTERNAL, "BOOLEAN", "工商");
        registerVar(registry, "legal_person_match", "法人匹配", VariableLayer.EXTERNAL, "BOOLEAN", "工商");

        // 司法变量
        registerVar(registry, "lawsuit_count", "涉诉数量", VariableLayer.EXTERNAL, "INTEGER", "司法");
        registerVar(registry, "execution_count", "被执行数量", VariableLayer.EXTERNAL, "INTEGER", "司法");
        registerVar(registry, "dishonest_count", "失信数量", VariableLayer.EXTERNAL, "INTEGER", "司法");
        registerVar(registry, "case_status", "案件状态", VariableLayer.EXTERNAL, "STRING", "司法");
        registerVar(registry, "judicial_risk_level", "司法风险等级", VariableLayer.EXTERNAL, "STRING", "司法");

        // 运营商变量
        registerVar(registry, "phone_real_name_match", "手机实名匹配", VariableLayer.EXTERNAL, "BOOLEAN", "运营商");
        registerVar(registry, "phone_active_months", "在网月数", VariableLayer.EXTERNAL, "INTEGER", "运营商");
        registerVar(registry, "phone_monthly_fee", "月均话费", VariableLayer.EXTERNAL, "DECIMAL", "运营商");
        registerVar(registry, "phone_area_match", "归属地匹配", VariableLayer.EXTERNAL, "BOOLEAN", "运营商");

        // 税务社保变量
        registerVar(registry, "tax_payment_amount", "纳税金额", VariableLayer.EXTERNAL, "DECIMAL", "税务");
        registerVar(registry, "tax_payment_months", "缴税月数", VariableLayer.EXTERNAL, "INTEGER", "税务");
        registerVar(registry, "social_security_months", "社保缴纳月数", VariableLayer.EXTERNAL, "INTEGER", "社保");
        registerVar(registry, "social_security_base", "社保基数", VariableLayer.EXTERNAL, "DECIMAL", "社保");
        registerVar(registry, "income_stability_score", "收入稳定性评分", VariableLayer.EXTERNAL, "DECIMAL", "税务");

        // 黑名单/舆情变量
        registerVar(registry, "blacklist_hit", "黑名单命中", VariableLayer.EXTERNAL, "BOOLEAN", "黑名单");
        registerVar(registry, "blacklist_sources", "黑名单来源", VariableLayer.EXTERNAL, "STRING", "黑名单");
        registerVar(registry, "negative_news_count", "负面新闻数量", VariableLayer.EXTERNAL, "INTEGER", "舆情");
        registerVar(registry, "risk_tag", "风险标签", VariableLayer.EXTERNAL, "STRING", "黑名单");
    }

    private void registerVar(VariableRegistry registry, String varId, String name,
                              VariableLayer layer, String dataType, String category) {
        registry.register(com.credit.platform.engine.core.variable.VariableDefinition.builder()
            .varId(varId)
            .name(name)
            .layer(layer)
            .dataType(dataType)
            .category(category)
            .build());
    }

    @Bean
    public AdapterRegistry adapterRegistry(ExternalApiProperties properties) {
        AdapterRegistry registry = new AdapterRegistry();

        // 注册 Mock 适配器
        registry.register(new CreditBureauMockAdapter(properties));
        registry.register(new BusinessRegistrationMockAdapter(properties));
        registry.register(new JudicialMockAdapter(properties));
        registry.register(new TelecomMockAdapter(properties));
        registry.register(new TaxSocialMockAdapter(properties));
        registry.register(new BlacklistMockAdapter(properties));

        log.info("AdapterRegistry initialized with {} adapters: {}",
            registry.size(), registry.getRegisteredTypes());

        return registry;
    }

    @Bean
    public ExternalApiProvider externalApiProvider(AdapterRegistry adapterRegistry,
                                                     @Qualifier("externalApiExecutor") Executor executor) {
        return new ExternalApiProvider(adapterRegistry, executor);
    }

    /**
     * 装配 VariableEngine — 注册 L1 ExternalApiProvider。
     * <p>
     * 注意：L0 (INPUT) 由 VariableResolveContext 自动处理，
     * L3 (DERIVED) 由 VariableEngine 内部 DerivedVariableProvider 处理。
     * 此处只注册 L1 (EXTERNAL)。
     * L2 (CACHED) 的 RedisFeatureProvider/HBaseFeatureProvider 需在后续增强中添加。
     * </p>
     */
    @Bean
    public VariableEngine variableEngine(VariableRegistry variableRegistry,
                                           ExternalApiProvider externalApiProvider) {
        VariableEngine engine = new VariableEngine(variableRegistry);
        engine.setProvider(VariableLayer.EXTERNAL, externalApiProvider);

        log.info("VariableEngine assembled with L1 ExternalApiProvider");
        return engine;
    }
}
