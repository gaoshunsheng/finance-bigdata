package com.credit.platform.admin.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 规则实体 — 通用规则/评分卡/决策流/变量的管理数据模型。
 * <p>
 * 包含版本管理和发布状态信息，支持完整的发布生命周期。
 * 当前使用内存存储，后续对接 MyBatis-Plus + MySQL。
 * </p>
 */
@TableName(value = "rule_entity", autoResultMap = true)
public class RuleEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String name;
    /** 类型: RULE / SCORECARD / DECISION_TABLE / DECISION_TREE / FLOW / VARIABLE */
    private String type;
    private int version;
    @TableField("status")
    private PublishStatus status;
    /** 规则 JSON 定义 */
    @TableField("content")
    private String content;
    /** 规则描述 */
    private String description;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 扩展属性 */
    @TableField(value = "attributes", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> attributes;

    public RuleEntity() {
        this.version = 1;
        this.status = PublishStatus.DRAFT;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.attributes = new LinkedHashMap<>();
    }

    /**
     * 创建新实体。
     */
    public static RuleEntity create(String id, String name, String type, String content) {
        RuleEntity entity = new RuleEntity();
        entity.id = Objects.requireNonNull(id);
        entity.name = name;
        entity.type = type;
        entity.content = content;
        return entity;
    }

    /**
     * 创建新版本副本。
     */
    public RuleEntity newVersion(int newVersion) {
        RuleEntity copy = new RuleEntity();
        copy.id = this.id;
        copy.name = this.name;
        copy.type = this.type;
        copy.version = newVersion;
        copy.status = PublishStatus.DRAFT;
        copy.content = this.content;
        copy.description = this.description;
        copy.attributes = new LinkedHashMap<>(this.attributes);
        return copy;
    }

    /** 转为摘要 Map (不含 content)。 */
    public Map<String, Object> toSummary() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("type", type);
        map.put("version", version);
        map.put("status", status.name());
        map.put("description", description);
        map.put("createdBy", createdBy);
        map.put("createdAt", createdAt != null ? createdAt.toString() : null);
        map.put("updatedAt", updatedAt != null ? updatedAt.toString() : null);
        return map;
    }

    // ========== Getters & Setters ==========

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public PublishStatus getStatus() { return status; }
    public void setStatus(PublishStatus status) { this.status = status; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; this.updatedAt = LocalDateTime.now(); }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
}
