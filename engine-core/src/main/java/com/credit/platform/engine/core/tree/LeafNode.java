package com.credit.platform.engine.core.tree;

import com.credit.platform.engine.core.compiler.model.RuleAction;
import java.util.Map;

/**
 * 决策树叶子节点 — 终态动作。
 * <p>
 * 不可变对象。evaluate 直接返回关联的动作。
 * </p>
 */
public final class LeafNode implements DecisionTreeNode {

    private final RuleAction action;

    public LeafNode(RuleAction action) {
        this.action = action;
    }

    @Override
    public boolean isLeaf() { return true; }

    @Override
    public RuleAction getAction() { return action; }

    @Override
    public RuleAction evaluate(Map<String, Object> variables) {
        return action;
    }
}
