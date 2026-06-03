package com.credit.platform.engine.core.tree;

import com.credit.platform.engine.core.compiler.model.RuleAction;
import java.util.Map;

/**
 * 决策树节点 — 条件分支或叶子动作。
 * <p>
 * 两种形态:
 * <ul>
 *   <li>分支节点: condition + trueChild/falseChild</li>
 *   <li>叶子节点: action (终态)</li>
 * </ul>
 * 深度优先遍历：从根节点开始，根据条件判断走 true 或 false 分支，直到叶子。
 * </p>
 */
public interface DecisionTreeNode {

    /**
     * 判断是否为叶子节点。
     */
    boolean isLeaf();

    /**
     * 获取叶子节点的动作。非叶子节点返回 null。
     */
    RuleAction getAction();

    /**
     * 沿决策树路径求值，返回最终命中的叶子动作。
     *
     * @param variables 变量上下文
     * @return 命中的叶子动作，或 null（未到达叶子）
     */
    RuleAction evaluate(Map<String, Object> variables);
}
