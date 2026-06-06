package com.credit.platform.engine.core.experiment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * AB 实验分流器 — 一致性哈希分流。
 * <p>
 * 基于 trafficKey 的 MD5 哈希值，按各分组 trafficRatio 比例分流。
 * 相同 trafficKey 始终分到同一组（一致性）。
 * </p>
 */
public class ExperimentSplitter {

    /**
     * 执行分流 — 返回 trafficKey 所属的分组 ID。
     *
     * @param config  实验配置
     * @param trafficKey 分流键值
     * @return 分组 ID
     */
    public static String split(ExperimentConfig config, String trafficKey) {
        if (config.getGroups() == null || config.getGroups().isEmpty()) {
            throw new IllegalArgumentException("Experiment config must have at least one group");
        }
        if (!config.isEnabled()) {
            return config.getGroups().get(0).getGroupId(); // 默认第一组
        }

        long hash = consistentHash(trafficKey, config.getExperimentId());
        double bucket = (hash & 0xFFFFL) / 65536.0; // 0.0 ~ 1.0

        double cumulative = 0.0;
        for (ExperimentConfig.GroupConfig group : config.getGroups()) {
            cumulative += group.getTrafficRatio();
            if (bucket < cumulative) {
                return group.getGroupId();
            }
        }
        return config.getGroups().get(config.getGroups().size() - 1).getGroupId();
    }

    /**
     * 一致性哈希 — SHA-256(trafficKey + experimentId) 取前 8 字节转 long。
     * <p>
     * 使用 SHA-256 而非 MD5，并在 fallback 中仍使用 SHA-256（Java 标准库保证可用），
     * 确保分布式环境下哈希结果一致。
     * </p>
     */
    static long consistentHash(String trafficKey, String experimentId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((trafficKey + ":" + experimentId)
                .getBytes(StandardCharsets.UTF_8));
            return ((long) (digest[0] & 0xFF) << 56)
                 | ((long) (digest[1] & 0xFF) << 48)
                 | ((long) (digest[2] & 0xFF) << 40)
                 | ((long) (digest[3] & 0xFF) << 32)
                 | ((long) (digest[4] & 0xFF) << 24)
                 | ((long) (digest[5] & 0xFF) << 16)
                 | ((long) (digest[6] & 0xFF) << 8)
                 | ((long) (digest[7] & 0xFF));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 Java 标准算法，理论上不会走到这里；
            // 作为最后防线，使用确定性的字符串哈希而非 JVM 依赖的 hashCode()
            return deterministicStringHash(trafficKey + ":" + experimentId);
        }
    }

    /**
     * 确定性字符串哈希 — 不依赖 String.hashCode() 的 JVM 实现。
     */
    private static long deterministicStringHash(String s) {
        long h = 0;
        for (int i = 0; i < s.length(); i++) {
            h = h * 31 + s.charAt(i);
        }
        return h;
    }
}
