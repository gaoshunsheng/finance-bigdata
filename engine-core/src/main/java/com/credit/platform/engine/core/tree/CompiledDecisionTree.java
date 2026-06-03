package com.credit.platform.engine.core.tree;

import com.credit.platform.engine.core.compiler.CompiledRule;
import com.credit.platform.engine.core.compiler.model.RuleAction;
import java.util.Map;
import java.util.Objects;

/**
 * 编译后的决策树 — 嵌套条件树，叶子节点是动作。
 * <p>
 * 不可变对象，线程安全。深度优先遍历执行。
 * </p>
 */
public final class CompiledDecisionTree implements CompiledRule {

    private static final String RULE_TYPE = "DECISION_TREE";

    private final String treeId;
    private final String name;
    private final int version;
    private final DecisionTreeNode root;

    public CompiledDecisionTree(String treeId, String name, int version, DecisionTreeNode root) {
        this.treeId = Objects.requireNonNull(treeId);
        this.name = name;
        this.version = version;
        this.root = Objects.requireNonNull(root);
    }

    /**
     * 执行决策树，返回叶子节点的动作。
     *
     * @param variables 变量上下文
     * @return 命中的叶子动作，无匹配时返回 null
     */
    public RuleAction evaluate(Map<String, Object> variables) {
        return root.evaluate(variables);
    }

    @Override
    public String getRuleId() { return treeId; }
    @Override
    public String getName() { return name; }
    @Override
    public int getVersion() { return version; }
    @Override
    public String getRuleType() { return RULE_TYPE; }

    public DecisionTreeNode getRoot() { return root; }

    @Override
    public String toString() {
        return "CompiledDecisionTree{id='" + treeId + "'}";
    }
}
