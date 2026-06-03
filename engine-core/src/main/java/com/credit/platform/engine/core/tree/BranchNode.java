package com.credit.platform.engine.core.tree;

import com.credit.platform.engine.core.compiler.model.ComparisonConditionNode;
import com.credit.platform.engine.core.compiler.model.ComparisonOperator;
import com.credit.platform.engine.core.compiler.model.ConditionNode;
import com.credit.platform.engine.core.compiler.model.LogicalConditionNode;
import com.credit.platform.engine.core.compiler.model.RuleAction;
import java.util.Map;
import java.util.Objects;

/**
 * 决策树分支节点 — 条件 + true/false 子树。
 * <p>
 * 不可变对象。条件可以是任何 {@link ConditionNode}（比较或逻辑组合）。
 * </p>
 */
public final class BranchNode implements DecisionTreeNode {

    private final ConditionNode condition;
    private final DecisionTreeNode trueChild;
    private final DecisionTreeNode falseChild;

    public BranchNode(ConditionNode condition, DecisionTreeNode trueChild, DecisionTreeNode falseChild) {
        this.condition = Objects.requireNonNull(condition);
        this.trueChild = Objects.requireNonNull(trueChild);
        this.falseChild = Objects.requireNonNull(falseChild);
    }

    @Override
    public boolean isLeaf() { return false; }

    @Override
    public RuleAction getAction() { return null; }

    @Override
    public RuleAction evaluate(Map<String, Object> variables) {
        if (condition.evaluate(variables)) {
            return trueChild.evaluate(variables);
        } else {
            return falseChild.evaluate(variables);
        }
    }

    public ConditionNode getCondition() { return condition; }
    public DecisionTreeNode getTrueChild() { return trueChild; }
    public DecisionTreeNode getFalseChild() { return falseChild; }
}
