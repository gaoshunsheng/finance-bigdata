package com.credit.platform.admin.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.credit.platform.admin.model.GrayscaleConfig;
import com.credit.platform.admin.model.GrayscaleConfig.GrayscaleStatus;
import com.credit.platform.admin.model.PublishStatus;
import com.credit.platform.admin.model.RuleEntity;

/**
 * 灰度发布服务 — 按百分比逐步提升流量。
 * <p>
 * 典型流程: APPROVED → 灰度5% → 25% → 50% → 100%(全量)。
 * 支持暂停/恢复/回滚灰度。
 * </p>
 */
@Service
public class GrayscalePublishService {

    /** 默认灰度阶梯 */
    private static final int[] DEFAULT_RAMP_STEPS = {5, 25, 50, 100};

    private final RuleRepository ruleRepository;
    private final ConcurrentHashMap<String, GrayscaleConfig> grayscaleStore = new ConcurrentHashMap<>();

    public GrayscalePublishService(RuleRepository ruleRepository) {
        this.ruleRepository = Objects.requireNonNull(ruleRepository);
    }

    /**
     * 开始灰度发布 — 将 APPROVED 状态的规则启动灰度。
     *
     * @param type               规则类型
     * @param id                 规则 ID
     * @param initialPercentage  初始灰度百分比 (默认 5%)
     * @param operator           操作人
     * @return GrayscaleConfig
     */
    public GrayscaleConfig startGrayscale(String type, String id, int initialPercentage, String operator) {
        RuleEntity entity = ruleRepository.findLatest(type, id)
            .orElseThrow(() -> new IllegalArgumentException(type + " not found: " + id));

        if (entity.getStatus() != PublishStatus.APPROVED) {
            throw new IllegalStateException(
                "Cannot start grayscale: current status is " + entity.getStatus()
                    + ", expected APPROVED");
        }

        // 检查是否已有灰度配置
        String configKey = configKey(type, id);
        GrayscaleConfig existing = grayscaleStore.get(configKey);
        if (existing != null && existing.getGrayscaleStatus() == GrayscaleStatus.IN_PROGRESS) {
            throw new IllegalStateException("Grayscale already in progress for " + type + ":" + id);
        }

        int percentage = initialPercentage > 0 ? initialPercentage : DEFAULT_RAMP_STEPS[0];
        GrayscaleConfig config = GrayscaleConfig.create(type, id, entity.getVersion());
        config.startGrayscale(percentage, operator);
        grayscaleStore.put(configKey, config);

        // 更新规则状态
        entity.setUpdatedBy(operator);
        entity.getAttributes().put("grayscalePercentage", percentage);

        // 如果直接 100%，跳过 GRAYSCALE 直接 RELEASED
        if (percentage >= 100) {
            entity.setStatus(PublishStatus.RELEASED);
            entity.getAttributes().put("releasedAt", java.time.LocalDateTime.now().toString());
            entity.getAttributes().put("releasedBy", operator);
        } else {
            entity.setStatus(PublishStatus.GRAYSCALE);
        }
        ruleRepository.save(entity);

        return config;
    }

    /**
     * 调整灰度百分比。
     *
     * @param type          规则类型
     * @param id            规则 ID
     * @param newPercentage 新百分比
     * @param operator      操作人
     * @return GrayscaleConfig
     */
    public GrayscaleConfig adjustGrayscale(String type, String id, int newPercentage, String operator) {
        GrayscaleConfig config = getGrayscaleConfig(type, id);
        if (config == null) {
            throw new IllegalArgumentException("No grayscale config found for " + type + ":" + id);
        }
        if (config.getGrayscaleStatus() != GrayscaleStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                "Cannot adjust: grayscale status is " + config.getGrayscaleStatus());
        }

        // 验证百分比合理性
        if (newPercentage <= config.getPercentage()) {
            throw new IllegalArgumentException(
                "New percentage must be greater than current " + config.getPercentage() + "%");
        }
        if (newPercentage > 100) {
            throw new IllegalArgumentException("Percentage cannot exceed 100%");
        }

        config.adjustPercentage(newPercentage, operator);

        // 更新规则属性
        RuleEntity entity = ruleRepository.findLatest(type, id)
            .orElseThrow(() -> new IllegalArgumentException(type + " not found: " + id));
        entity.getAttributes().put("grayscalePercentage", newPercentage);
        ruleRepository.save(entity);

        // 如果达到 100%，推进到 RELEASED
        if (newPercentage >= 100) {
            entity.setStatus(PublishStatus.RELEASED);
            entity.getAttributes().put("releasedAt", java.time.LocalDateTime.now().toString());
            entity.getAttributes().put("releasedBy", operator);
            ruleRepository.save(entity);
        }

        return config;
    }

    /**
     * 按推荐阶梯提升灰度。
     *
     * @param type     规则类型
     * @param id       规则 ID
     * @param operator 操作人
     * @return GrayscaleConfig
     */
    public GrayscaleConfig rampUp(String type, String id, String operator) {
        GrayscaleConfig config = getGrayscaleConfig(type, id);
        if (config == null) {
            throw new IllegalArgumentException("No grayscale config found for " + type + ":" + id);
        }

        int currentPercentage = config.getPercentage();
        int nextStep = findNextRampStep(currentPercentage);
        return adjustGrayscale(type, id, nextStep, operator);
    }

    /**
     * 暂停灰度。
     */
    public GrayscaleConfig pause(String type, String id, String operator) {
        GrayscaleConfig config = getGrayscaleConfig(type, id);
        if (config == null) {
            throw new IllegalArgumentException("No grayscale config found for " + type + ":" + id);
        }
        config.pause(operator);
        return config;
    }

    /**
     * 恢复灰度。
     */
    public GrayscaleConfig resume(String type, String id, String operator) {
        GrayscaleConfig config = getGrayscaleConfig(type, id);
        if (config == null) {
            throw new IllegalArgumentException("No grayscale config found for " + type + ":" + id);
        }
        config.resume(operator);
        return config;
    }

    /**
     * 回滚灰度 — 将流量归零，规则回到 APPROVED 状态。
     */
    public RuleEntity rollbackGrayscale(String type, String id, String operator) {
        GrayscaleConfig config = getGrayscaleConfig(type, id);
        if (config == null) {
            throw new IllegalArgumentException("No grayscale config found for " + type + ":" + id);
        }

        config.adjustPercentage(0, operator);

        RuleEntity entity = ruleRepository.findLatest(type, id)
            .orElseThrow(() -> new IllegalArgumentException(type + " not found: " + id));
        entity.setStatus(PublishStatus.APPROVED);
        entity.setUpdatedBy(operator);
        entity.getAttributes().put("grayscalePercentage", 0);
        entity.getAttributes().put("grayscaleRolledBackAt", java.time.LocalDateTime.now().toString());
        ruleRepository.save(entity);

        return entity;
    }

    /**
     * 获取灰度配置。
     */
    public GrayscaleConfig getGrayscaleConfig(String type, String id) {
        return grayscaleStore.get(configKey(type, id));
    }

    /**
     * 获取所有灰度中的配置。
     */
    public List<GrayscaleConfig> listActiveGrayscales() {
        return grayscaleStore.values().stream()
            .filter(c -> c.getGrayscaleStatus() == GrayscaleStatus.IN_PROGRESS)
            .collect(Collectors.toList());
    }

    /**
     * 获取推荐阶梯的下一步。
     */
    private int findNextRampStep(int currentPercentage) {
        for (int step : DEFAULT_RAMP_STEPS) {
            if (step > currentPercentage) {
                return step;
            }
        }
        return 100;
    }

    private String configKey(String type, String id) {
        return type + ":" + id;
    }
}
