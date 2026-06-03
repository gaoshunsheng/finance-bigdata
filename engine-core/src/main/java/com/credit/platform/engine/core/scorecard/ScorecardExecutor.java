package com.credit.platform.engine.core.scorecard;

import com.credit.platform.engine.core.executor.ExecutionContext;
import java.util.ArrayList;
import java.util.List;

/**
 * 评分卡执行器 — 对执行上下文计算评分卡。
 * <p>
 * 无状态，线程安全。
 * </p>
 *
 * <pre>
 * ScorecardExecutor executor = new ScorecardExecutor();
 * ScorecardResult result = executor.execute(compiledScorecard, context);
 * </pre>
 */
public class ScorecardExecutor {

    /**
     * 执行评分卡计算。
     * <p>
     * 执行流程:
     * <ol>
     *   <li>初始分 = initialScore</li>
     *   <li>遍历每个特征: 获取变量值 → 查找分箱 → 累加得分 → 记录明细</li>
     *   <li>根据 cutoff 判定最终结果</li>
     * </ol>
     * </p>
     *
     * @param scorecard 编译后的评分卡
     * @param context   执行上下文
     * @return 评分结果（含得分明细）
     */
    public ScorecardResult execute(CompiledScorecard scorecard, ExecutionContext context) {
        long start = System.nanoTime();

        int totalScore = scorecard.getInitialScore();
        List<ScorecardResult.CharacteristicScore> breakdown = new ArrayList<>();

        for (CompiledScorecard.Characteristic characteristic : scorecard.getCharacteristics()) {
            Object value = context.getVariable(characteristic.getField());
            CompiledScorecard.Bin bin = characteristic.findBin(value);

            int binScore = 0;
            String binLabel = "N/A";

            if (bin != null) {
                binScore = bin.getScore();
                binLabel = bin.getLabel();
            }

            totalScore += binScore;

            breakdown.add(new ScorecardResult.CharacteristicScore(
                characteristic.getName(),
                characteristic.getField(),
                value,
                binLabel,
                binScore
            ));
        }

        String result = scorecard.getCutoff().decide(totalScore);

        long durationMs = (System.nanoTime() - start) / 1_000_000;

        return ScorecardResult.builder()
            .scorecardId(scorecard.getRuleId())
            .initialScore(scorecard.getInitialScore())
            .finalScore(totalScore)
            .result(result)
            .breakdown(breakdown)
            .durationMs(durationMs)
            .build();
    }
}
