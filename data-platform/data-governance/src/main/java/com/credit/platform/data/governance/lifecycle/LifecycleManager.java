package com.credit.platform.data.governance.lifecycle;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 生命周期管理器 — 按数据层和年龄管理冷热分层策略。
 *
 * <p>分层策略:
 * <table>
 *   <tr><th>数据层</th><th>热数据</th><th>温数据</th><th>冷数据</th><th>归档</th></tr>
 *   <tr><td>ODS</td><td>30天</td><td>6个月</td><td>2年</td><td>归档至冷存储</td></tr>
 *   <tr><td>DWD</td><td>90天</td><td>1年</td><td>3年</td><td>归档至冷存储</td></tr>
 *   <tr><td>DWS</td><td>6个月</td><td>2年</td><td>3年</td><td>归档至冷存储</td></tr>
 *   <tr><td>ADS</td><td>1年</td><td>3年</td><td>5年</td><td>归档</td></tr>
 *   <tr><td>ES日志</td><td>7天(SSD)</td><td>90天(HDD)</td><td>1年(冷)</td><td>ILM自动管理</td></tr>
 * </table>
 */
public class LifecycleManager {

    /** 各层的生命周期配置 */
    private final Map<String, LifecyclePolicy> policies = new LinkedHashMap<>();

    public LifecycleManager() {
        // 初始化各层默认策略
        policies.put("ODS", new LifecyclePolicy("ODS", 30, 180, 730, 730));
        policies.put("DWD", new LifecyclePolicy("DWD", 90, 365, 1095, 1825));
        policies.put("DWS", new LifecyclePolicy("DWS", 180, 730, 1095, 1825));
        policies.put("ADS", new LifecyclePolicy("ADS", 365, 1095, 1825, 1825));
        policies.put("ES_LOG", new LifecyclePolicy("ES_LOG", 7, 90, 365, 1825));
    }

    /**
     * 判断数据应处于哪个温度层。
     *
     * @param layer     数据层 (ODS/DWD/DWS/ADS)
     * @param createDate 数据创建日期
     * @return 温度层级 (HOT/WARM/COLD/ARCHIVE)
     */
    public TemperatureTier determineTier(String layer, LocalDate createDate) {
        LifecyclePolicy policy = policies.get(layer.toUpperCase());
        if (policy == null) {
            return TemperatureTier.HOT;
        }

        long ageInDays = ChronoUnit.DAYS.between(createDate, LocalDate.now());

        if (ageInDays <= policy.hotDays) {
            return TemperatureTier.HOT;
        } else if (ageInDays <= policy.warmDays) {
            return TemperatureTier.WARM;
        } else if (ageInDays <= policy.coldDays) {
            return TemperatureTier.COLD;
        } else {
            return TemperatureTier.ARCHIVE;
        }
    }

    /**
     * 获取指定层的生命周期策略。
     */
    public LifecyclePolicy getPolicy(String layer) {
        return policies.get(layer.toUpperCase());
    }

    /**
     * 获取需要归档的数据日期边界（该日期之前的数据应归档）。
     */
    public LocalDate getArchiveThreshold(String layer) {
        LifecyclePolicy policy = policies.get(layer.toUpperCase());
        if (policy == null) return null;
        return LocalDate.now().minusDays(policy.coldDays);
    }

    /**
     * 扫描指定层的数据分区，返回应迁移的分区列表。
     *
     * @param layer    数据层
     * @param partitions 已有分区日期列表
     * @return 按温度层分组的分区列表
     */
    public Map<TemperatureTier, List<LocalDate>> scanPartitions(String layer, List<LocalDate> partitions) {
        Map<TemperatureTier, List<LocalDate>> result = new EnumMap<>(TemperatureTier.class);
        for (TemperatureTier tier : TemperatureTier.values()) {
            result.put(tier, new ArrayList<>());
        }

        for (LocalDate partition : partitions) {
            TemperatureTier tier = determineTier(layer, partition);
            result.get(tier).add(partition);
        }

        return result;
    }

    /** 温度层级 */
    public enum TemperatureTier {
        HOT("热数据", "SSD 高性能存储"),
        WARM("温数据", "HDD 标准存储"),
        COLD("冷数据", "低成本归档存储"),
        ARCHIVE("归档数据", "长期归档存储");

        private final String name;
        private final String storageType;

        TemperatureTier(String name, String storageType) {
            this.name = name;
            this.storageType = storageType;
        }

        public String getName() { return name; }
        public String getStorageType() { return storageType; }
    }

    /** 生命周期策略配置 */
    public static class LifecyclePolicy {
        private final String layer;
        private final int hotDays;
        private final int warmDays;
        private final int coldDays;
        private final int retentionDays;

        public LifecyclePolicy(String layer, int hotDays, int warmDays, int coldDays, int retentionDays) {
            this.layer = layer;
            this.hotDays = hotDays;
            this.warmDays = warmDays;
            this.coldDays = coldDays;
            this.retentionDays = retentionDays;
        }

        public String getLayer() { return layer; }
        public int getHotDays() { return hotDays; }
        public int getWarmDays() { return warmDays; }
        public int getColdDays() { return coldDays; }
        public int getRetentionDays() { return retentionDays; }

        @Override
        public String toString() {
            return String.format("LifecyclePolicy{%s: hot=%dd, warm=%dd, cold=%dd, retention=%dd}",
                    layer, hotDays, warmDays, coldDays, retentionDays);
        }
    }
}
