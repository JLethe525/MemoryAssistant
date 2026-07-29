package com.hqu.memory.service;

import java.time.LocalDate;

/**
 * 艾宾浩斯遗忘曲线间隔重复算法
 *
 * 间隔数组 [0, 1, 3, 7, 15, 30] 天
 * stage 0 → 5，数字越大代表记忆越牢固，下次复习间隔越长
 */
public class SpacedRepetition {

    /** 遗忘曲线间隔（天） */
    public static final int[] INTERVALS = {0, 1, 3, 7, 15, 30};

    public enum Difficulty {
        EASY,   // 记得住 → stage+1，间隔翻倍
        MEDIUM, // 有点模糊 → stage 不变
        HARD    // 忘记了 → stage-1，回到更短间隔
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
     *
     * @param stage    当前 stage (0-5)
     * @param diff     用户评级
     * @param today    今天的日期
     * @return ReviewResult 包含新 stage 和下次复习日期
     */
    public static ReviewResult calculate(int stage, Difficulty diff, LocalDate today) {
        int newStage;
        switch (diff) {
            case EASY:
                newStage = Math.min(stage + 1, 5);
                break;
            case MEDIUM:
                newStage = stage; // 保持当前阶段
                break;
            case HARD:
                newStage = Math.max(stage - 1, 0);
                break;
            default:
                newStage = stage;
        }
        LocalDate nextDate = today.plusDays(INTERVALS[newStage]);
        return new ReviewResult(newStage, nextDate);
    }
}
