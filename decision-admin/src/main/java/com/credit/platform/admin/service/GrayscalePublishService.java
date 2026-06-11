package com.credit.platform.admin.service;

import java.util.List;
import java.util.Objects;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.credit.platform.admin.mapper.GrayscaleConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.credit.platform.admin.model.GrayscaleConfig;
import com.credit.platform.admin.model.GrayscaleConfig.GrayscaleStatus;
import com.credit.platform.admin.model.PublishStatus;
import com.credit.platform.admin.model.RuleEntity;

/**
 * 灰度发布服务 — 按百分比逐步提升流量。
 * <p>
 * 典型流程: APPROVED → 灰度5% → 25% → 50% → 100%(全量)。
 * 支持暂停/恢复/回滚灰度。
 * 持久化到 grayscale_config 表。
 * </p>
 */
@Service
public class GrayscalePublishService {

    /** 默认灰度阶梯 */
    private static final int[] DEFAULT_RAMP_STEPS = {5, 25, 50, 100};

    private final RuleRepository ruleRepository;
    private final GrayscaleConfigMapper grayscaleConfigMapper;

    public GrayscalePublishService(RuleRepository ruleRepository, GrayscaleConfigMapper grayscaleConfigMapper) {
        this.ruleRepository = Objects.requireNonNull(ruleRepository);
        this.grayscaleConfigMapper = grayscaleConfigMapper;
    }

    /**
     * 开始灰度发布 — 将 APPROVED 状态的规则启动灰度。
     */
    @Transactional
    public GrayscaleConfig startGrayscale(String type, String id, int initialPercentage, String operator) {
        RuleEntity entity = ruleRepository.findLatest(type, id)
            .orElseThrow(() -> new IllegalArgumentException(type + " not found: " + id));

        if (entity.getStatus() != PublishStatus.APPROVED) {
            throw new IllegalStateException(
                "Cannot start grayscale: current status is " + entity.getStatus()
                    + ", expected APPROVED");
        }

        // 检查是否已有灰度配置
        GrayscaleConfig existing = findConfig(type, id);
        if (existing != null && existing.getGrayscaleStatus() == GrayscaleStatus.IN_PROGRESS) {
            throw new IllegalStateException("Grayscale already in progress for " + type + ":" + id);
        }

        int percentage = initialPercentage > 0 ? initialPercentage : DEFAULT_RAMP_STEPS[0];
        GrayscaleConfig config = GrayscaleConfig.create(type, id, entity.getVersion());
        config.startGrayscale(percentage, operator);

        // 保存到数据库（如果已存在则更新）
        if (existing != null) {
            config.setConfigId(existing.getConfigId());
            grayscaleConfigMapper.update(config, new LambdaQueryWrapper<GrayscaleConfig>()
                    .eq(GrayscaleConfig::getConfigId, existing.getConfigId()));
        } else {
            grayscaleConfigMapper.insert(config);
        }

        // 更新规则状态
        entity.setUpdatedBy(operator);
        entity.getAttributes().put("grayscalePercentage", percentage);

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
     */
    public GrayscaleConfig adjustGrayscale(String type, String id, int newPercentage, String operator) {
        GrayscaleConfig config = findConfig(type, id);
        if (config == null) {
            throw new IllegalArgumentException("No grayscale config found for " + type + ":" + id);
        }
        if (config.getGrayscaleStatus() != GrayscaleStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                "Cannot adjust: grayscale status is " + config.getGrayscaleStatus());
        }

        if (newPercentage <= config.getPercentage()) {
            throw new IllegalArgumentException(
                "New percentage must be greater than current " + config.getPercentage() + "%");
        }
        if (newPercentage > 100) {
            throw new IllegalArgumentException("Percentage cannot exceed 100%");
        }

        config.adjustPercentage(newPercentage, operator);
        grayscaleConfigMapper.update(config, new LambdaQueryWrapper<GrayscaleConfig>()
                .eq(GrayscaleConfig::getConfigId, config.getConfigId()));

        RuleEntity entity = ruleRepository.findLatest(type, id)
            .orElseThrow(() -> new IllegalArgumentException(type + " not found: " + id));
        entity.getAttributes().put("grayscalePercentage", newPercentage);
        ruleRepository.save(entity);

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
     */
    public GrayscaleConfig rampUp(String type, String id, String operator) {
        GrayscaleConfig config = findConfig(type, id);
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
        GrayscaleConfig config = findConfig(type, id);
        if (config == null) {
            throw new IllegalArgumentException("No grayscale config found for " + type + ":" + id);
        }
        config.pause(operator);
        grayscaleConfigMapper.update(config, new LambdaQueryWrapper<GrayscaleConfig>()
                .eq(GrayscaleConfig::getConfigId, config.getConfigId()));
        return config;
    }

    /**
     * 恢复灰度。
     */
    public GrayscaleConfig resume(String type, String id, String operator) {
        GrayscaleConfig config = findConfig(type, id);
        if (config == null) {
            throw new IllegalArgumentException("No grayscale config found for " + type + ":" + id);
        }
        config.resume(operator);
        grayscaleConfigMapper.update(config, new LambdaQueryWrapper<GrayscaleConfig>()
                .eq(GrayscaleConfig::getConfigId, config.getConfigId()));
        return config;
    }

    /**
     * 回滚灰度 — 将流量归零，规则回到 APPROVED 状态。
     */
    public RuleEntity rollbackGrayscale(String type, String id, String operator) {
        GrayscaleConfig config = findConfig(type, id);
        if (config == null) {
            throw new IllegalArgumentException("No grayscale config found for " + type + ":" + id);
        }

        config.adjustPercentage(0, operator);
        grayscaleConfigMapper.update(config, new LambdaQueryWrapper<GrayscaleConfig>()
                .eq(GrayscaleConfig::getConfigId, config.getConfigId()));

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
        return findConfig(type, id);
    }

    /**
     * 获取所有灰度中的配置。
     */
    public List<GrayscaleConfig> listActiveGrayscales() {
        return grayscaleConfigMapper.listActiveGrayscales();
    }

    /**
     * 获取全部灰度配置（含已发布、灰度中、未开始）。
     */
    public List<GrayscaleConfig> listAllGrayscales() {
        return grayscaleConfigMapper.selectList(null);
    }

    private GrayscaleConfig findConfig(String type, String id) {
        return grayscaleConfigMapper.findByTarget(type, id).orElse(null);
    }

    private int findNextRampStep(int currentPercentage) {
        for (int step : DEFAULT_RAMP_STEPS) {
            if (step > currentPercentage) {
                return step;
            }
        }
        return 100;
    }
}
