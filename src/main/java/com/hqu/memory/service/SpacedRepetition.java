package com.hqu.memory.service;

import java.time.LocalDate;

/**
 * 艾宾浩斯遗忘曲线间隔重复算法
 * 【讲解重点：这是项目核心，答辩时必问】
 *
 * 艾宾浩斯遗忘曲线原理：
 *   人脑记忆会随时间衰减，刚学完时遗忘最快，越往后遗忘越慢。
 *   在关键时间点（1天、3天、7天、15天、30天）复习，
 *   可以大幅提高长期记忆效果。
 *
 * 间隔数组 [0, 1, 3, 7, 15, 30] 天
 * stage 0 → 5，数字越大代表记忆越牢固，下次复习间隔越长
 * stage 5 表示"已掌握"，每 30 天复习一次维持即可
 */
public class SpacedRepetition {

    /** 遗忘曲线间隔（天）—— 核心数据 */
    public static final int[] INTERVALS = {0, 1, 3, 7, 15, 30};

    public enum Difficulty {
        EASY,   // 记得住 → stage+1，进入更长间隔
        MEDIUM, // 有点模糊 → stage 不变，保持当前间隔
        HARD    // 忘记了 → stage-1，回到更短间隔，增加复习频率
    }

    /** 计算结果：新的 stage 和 下次复习日期 */
    public static class ReviewResult {
        public final int newStage;
        public final LocalDate nextReviewDate;

        public ReviewResult(int newStage, LocalDate nextReviewDate) {
            this.newStage = newStage;
            this.nextReviewDate = nextReviewDate;
        }
    }

    /**
     * 根据当前 stage 和用户评级，计算下一步复习安排
     * 【讲解重点：算法逻辑】
     *
     * @param stage    当前 stage (0-5)
     * @param diff     用户评级
     * @param today    今天的日期
     * @return ReviewResult 包含新 stage 和下次复习日期
     *
     * 例子：
     *   当前 stage=2（3天间隔），评级 EASY：
     *     newStage = min(2+1, 5) = 3
     *     下次复习 = 今天 + 7天
     *
     *   当前 stage=2，评级 HARD：
     *     newStage = max(2-1, 0) = 1
     *     下次复习 = 今天 + 1天
     */
    public static ReviewResult calculate(int stage, Difficulty diff, LocalDate today) {
        int newStage;
        switch (diff) {
            case EASY:
                newStage = Math.min(stage + 1, 5);   // 上限 5 级
                break;
            case MEDIUM:
                newStage = stage;                    // 保持不动
                break;
            case HARD:
                newStage = Math.max(stage - 1, 0);   // 下限 0 级
                break;
            default:
                newStage = stage;
        }
        LocalDate nextDate = today.plusDays(INTERVALS[newStage]);
        return new ReviewResult(newStage, nextDate);
    }
}
