package com.credit.platform.admin.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 灰度发布配置 — 按百分比逐步提升流量。
 * <p>
 * 典型流程: 5% → 25% → 50% → 100% (全量发布)。
 * 每次调整灰度比例生成新记录，完整追溯灰度过程。
 * </p>
 */
@TableName("grayscale_config")
public class GrayscaleConfig {

    /** 灰度配置 ID */
    @TableId(type = IdType.INPUT)
    private String configId;
    /** 关联的规则类型 */
    @TableField("target_type")
    private String targetType;
    /** 关联的规则 ID */
    @TableField("target_id")
    private String targetId;
    /** 关联的规则版本 */
    @TableField("target_version")
    private int targetVersion;
    /** 当前灰度百分比 (0-100) */
    @TableField("percentage")
    private int percentage;
    /** 上一次灰度百分比 */
    @TableField("previous_percentage")
    private int previousPercentage;
    /** 操作人 */
    @TableField("operator")
    private String operator;
    /** 灰度开始时间 */
    @TableField("started_at")
    private LocalDateTime startedAt;
    /** 最后调整时间 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
    /** 灰度状态 */
    @TableField("grayscale_status")
    private GrayscaleStatus grayscaleStatus;

    public GrayscaleConfig() {
        this.percentage = 0;
        this.previousPercentage = 0;
        this.grayscaleStatus = GrayscaleStatus.NOT_STARTED;
    }

    /**
     * 灰度发布状态。
     */
    public enum GrayscaleStatus {
        /** 未开始 */
        NOT_STARTED("未开始"),
        /** 灰度中 */
        IN_PROGRESS("灰度中"),
        GRAYSCALE("灰度中"),
        /** 已全量 */
        FULL("已全量"),
        RELEASED("已全量"),
        /** 已暂停 */
        PAUSED("已暂停"),
        /** 已回滚 */
        ROLLED_BACK("已回滚");

        private final String description;

        GrayscaleStatus(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    /**
     * 创建初始灰度配置。
     */
    public static GrayscaleConfig create(String targetType, String targetId, int targetVersion) {
        GrayscaleConfig config = new GrayscaleConfig();
        config.configId = "gs-" + System.currentTimeMillis();
        config.targetType = Objects.requireNonNull(targetType);
        config.targetId = Objects.requireNonNull(targetId);
        config.targetVersion = targetVersion;
        return config;
    }

    /**
     * 调整灰度百分比。
     *
     * @param newPercentage 新的百分比 (1-100)
     * @param operator      操作人
     * @return 是否成功
     */
    public boolean adjustPercentage(int newPercentage, String operator) {
        if (newPercentage < 0 || newPercentage > 100) {
            return false;
        }
        if (newPercentage <= this.percentage && newPercentage != 0) {
            return false; // 只允许递增或归零(回滚)
        }
        this.previousPercentage = this.percentage;
        this.percentage = newPercentage;
        this.operator = operator;
        this.updatedAt = LocalDateTime.now();

        if (newPercentage == 0) {
            this.grayscaleStatus = GrayscaleStatus.ROLLED_BACK;
        } else if (newPercentage >= 100) {
            this.grayscaleStatus = GrayscaleStatus.FULL;
        } else {
            this.grayscaleStatus = GrayscaleStatus.IN_PROGRESS;
        }

        if (this.startedAt == null && newPercentage > 0) {
            this.startedAt = LocalDateTime.now();
        }
        return true;
    }

    /**
     * 开始灰度 (首次设置百分比)。
     */
    public void startGrayscale(int initialPercentage, String operator) {
        if (grayscaleStatus != GrayscaleStatus.NOT_STARTED) {
            throw new IllegalStateException("Grayscale already started");
        }
        this.operator = operator;
        this.startedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.adjustPercentage(initialPercentage, operator);
    }

    /**
     * 暂停灰度。
     */
    public void pause(String operator) {
        if (grayscaleStatus != GrayscaleStatus.IN_PROGRESS) {
            throw new IllegalStateException("Can only pause grayscale in IN_PROGRESS status");
        }
        this.operator = operator;
        this.grayscaleStatus = GrayscaleStatus.PAUSED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 恢复灰度。
     */
    public void resume(String operator) {
        if (grayscaleStatus != GrayscaleStatus.PAUSED) {
            throw new IllegalStateException("Can only resume grayscale in PAUSED status");
        }
        this.operator = operator;
        this.grayscaleStatus = GrayscaleStatus.IN_PROGRESS;
        this.updatedAt = LocalDateTime.now();
    }

    // ========== Getters & Setters ==========

    public String getConfigId() { return configId; }
    public void setConfigId(String configId) { this.configId = configId; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public int getTargetVersion() { return targetVersion; }
    public void setTargetVersion(int targetVersion) { this.targetVersion = targetVersion; }
    public int getPercentage() { return percentage; }
    public void setPercentage(int percentage) { this.percentage = percentage; }
    public int getPreviousPercentage() { return previousPercentage; }
    public void setPreviousPercentage(int previousPercentage) { this.previousPercentage = previousPercentage; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public GrayscaleStatus getGrayscaleStatus() { return grayscaleStatus; }
    public void setGrayscaleStatus(GrayscaleStatus grayscaleStatus) { this.grayscaleStatus = grayscaleStatus; }
}
