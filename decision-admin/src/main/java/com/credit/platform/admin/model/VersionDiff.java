package com.credit.platform.admin.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 版本差异 — 描述两个版本之间的内容差异。
 * <p>
 * 支持 JSON 规则内容的字段级差异检测:
 * - ADDED: 新增字段
 * - REMOVED: 删除字段
 * - MODIFIED: 修改字段
 * - UNCHANGED: 未变字段
 * </p>
 */
public class VersionDiff {

    private final String targetType;
    private final String targetId;
    private final int sourceVersion;
    private final int targetVersion;
    private final List<FieldDiff> diffs;
    private final int addedCount;
    private final int removedCount;
    private final int modifiedCount;
    private final int unchangedCount;

    private VersionDiff(Builder builder) {
        this.targetType = builder.targetType;
        this.targetId = builder.targetId;
        this.sourceVersion = builder.sourceVersion;
        this.targetVersion = builder.targetVersion;
        this.diffs = Collections.unmodifiableList(new ArrayList<>(builder.diffs));
        this.addedCount = (int) builder.diffs.stream().filter(d -> d.type == DiffType.ADDED).count();
        this.removedCount = (int) builder.diffs.stream().filter(d -> d.type == DiffType.REMOVED).count();
        this.modifiedCount = (int) builder.diffs.stream().filter(d -> d.type == DiffType.MODIFIED).count();
        this.unchangedCount = (int) builder.diffs.stream().filter(d -> d.type == DiffType.UNCHANGED).count();
    }

    /**
     * 字段差异类型。
     */
    public enum DiffType {
        ADDED, REMOVED, MODIFIED, UNCHANGED
    }

    /**
     * 单个字段差异。
     */
    public static class FieldDiff {
        private final String fieldPath;
        private final DiffType type;
        private final String oldValue;
        private final String newValue;

        public FieldDiff(String fieldPath, DiffType type, String oldValue, String newValue) {
            this.fieldPath = Objects.requireNonNull(fieldPath);
            this.type = Objects.requireNonNull(type);
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public String getFieldPath() { return fieldPath; }
        public DiffType getType() { return type; }
        public String getOldValue() { return oldValue; }
        public String getNewValue() { return newValue; }
    }

    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public int getSourceVersion() { return sourceVersion; }
    public int getTargetVersion() { return targetVersion; }
    public List<FieldDiff> getDiffs() { return diffs; }
    public int getAddedCount() { return addedCount; }
    public int getRemovedCount() { return removedCount; }
    public int getModifiedCount() { return modifiedCount; }
    public int getUnchangedCount() { return unchangedCount; }
    public boolean hasChanges() { return addedCount > 0 || removedCount > 0 || modifiedCount > 0; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String targetType;
        private String targetId;
        private int sourceVersion;
        private int targetVersion;
        private final List<FieldDiff> diffs = new ArrayList<>();

        public Builder targetType(String targetType) { this.targetType = targetType; return this; }
        public Builder targetId(String targetId) { this.targetId = targetId; return this; }
        public Builder sourceVersion(int v) { this.sourceVersion = v; return this; }
        public Builder targetVersion(int v) { this.targetVersion = v; return this; }
        public Builder addDiff(FieldDiff diff) { this.diffs.add(diff); return this; }
        public VersionDiff build() { return new VersionDiff(this); }
    }
}
