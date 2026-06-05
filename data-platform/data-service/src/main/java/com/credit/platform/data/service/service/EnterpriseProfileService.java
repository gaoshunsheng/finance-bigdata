package com.credit.platform.data.service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 企业画像查询服务 — 聚合多个数据源生成统一企业画像。
 *
 * <p>数据源:
 * <ul>
 *   <li>内部: 数仓客户维度数据（DWS 层）</li>
 *   <li>外部: 工商/司法/税务/运营商等外部 API 数据</li>
 *   <li>决策: 历史决策记录（ES）</li>
 * </ul>
 *
 * <p>目标: P99 < 500ms
 */
@Service
public class EnterpriseProfileService {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseProfileService.class);

    /**
     * 查询企业画像。
     *
     * @param enterpriseId 企业 ID
     * @return 企业画像数据（多源聚合）
     */
    public Map<String, Object> queryProfile(String enterpriseId) {
        long start = System.currentTimeMillis();

        Map<String, Object> profile = new LinkedHashMap<>();

        // 基本信息（模拟从 DWS 层查询）
        profile.put("basicInfo", mockBasicInfo(enterpriseId));

        // 工商信息（模拟从外部 API）
        profile.put("businessRegistration", mockBusinessRegistration(enterpriseId));

        // 信用摘要（模拟从 ES 查询）
        profile.put("creditSummary", mockCreditSummary(enterpriseId));

        // 关联人员
        profile.put("relatedPersons", List.of(
                Map.of("name", "张**", "role", "法人", "idCard", "110***********1234")
        ));

        // 风险信号
        profile.put("riskSignals", List.of(
                Map.of("type", "OVERDUE", "level", "WARNING", "description", "近6月逾期2次")
        ));

        // 数据完整性
        List<String> availableSources = List.of("internal", "business_registration", "credit_bureau");
        List<String> missingSources = List.of("judicial", "telecom");
        profile.put("dataSourceStatus", Map.of(
                "available", availableSources,
                "missing", missingSources
        ));

        long elapsed = System.currentTimeMillis() - start;
        log.info("企业画像查询完成: enterpriseId={}, 耗时={}ms", enterpriseId, elapsed);
        return profile;
    }

    private Map<String, Object> mockBasicInfo(String enterpriseId) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("enterpriseId", enterpriseId);
        info.put("enterpriseName", "示例科技有限公司");
        info.put("unifiedSocialCreditCode", "91110108MA01XXXXX");
        info.put("registeredCapital", "1000万元");
        info.put("establishedDate", "2015-03-15");
        info.put("legalPerson", "张**");
        info.put("industry", "信息技术");
        info.put("status", "存续");
        return info;
    }

    private Map<String, Object> mockBusinessRegistration(String enterpriseId) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("registrationStatus", "存续（在营）");
        info.put("businessScope", "技术开发、技术咨询、技术服务");
        info.put("registeredAddress", "北京市海淀区XXX路XXX号");
        info.put("lastUpdateTime", "2026-01-15");
        return info;
    }

    private Map<String, Object> mockCreditSummary(String enterpriseId) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("totalLoanCount", 5);
        info.put("totalLoanAmount", 5000000.00);
        info.put("activeLoanCount", 2);
        info.put("activeLoanAmount", 2000000.00);
        info.put("overdueCount", 1);
        info.put("maxOverdueDays", 15);
        info.put("creditScore", 680);
        info.put("riskLevel", "MEDIUM");
        return info;
    }
}
