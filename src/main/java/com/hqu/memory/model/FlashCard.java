package com.hqu.memory.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 闪卡数据模型
 * 【讲解重点：这是整个应用的数据基础】
 *
 * 每张卡片包含：
 * - 正反面内容（题目 + 答案）
 * - 记忆阶段 stage（0→5，决定下次复习间隔）
 * - 复习记录（记得/模糊/不记得的次数）
 * - 收藏状态
 *
 * stage 0-5 对应遗忘曲线的 6 个间隔：
 *   stage 0 → 立即复习（刚创建）
 *   stage 1 → 1天后复习
 *   stage 2 → 3天后复习
 *   stage 3 → 7天后复习
 *   stage 4 → 15天后复习
 *   stage 5 → 30天后复习（已掌握状态，长期维持）
 *
 * Gson 序列化原理：通过 getter/setter 自动将对象转为 JSON 字符串，
 * 存到本地文件。加载时再转回来。所以每个字段都需要 get/set。
 */
public class FlashCard {
    private String id;
    private String front;       // 正面（题目）
    private String back;        // 背面（答案）
    private String category;    // 分类（政治/英语/数学/专业课等）
    private int stage;          // 记忆阶段 0-5【核心字段，驱动遗忘曲线算法】
    private String nextReviewDate;  // 下次复习日期 (ISO: yyyy-MM-dd)
    private String lastReviewDate;  // 上次复习日期
    private String createdDate;     // 创建日期
    private int easyCount;      // 记得次数【统计用，展示学习成果】
    private int mediumCount;    // 模糊次数
    private int hardCount;      // 不记得次数
    private boolean starred;    // 是否收藏【方便筛选重点卡片】

    // Gson 反序列化需要无参构造
    public FlashCard() {}

    public FlashCard(String front, String back, String category) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.front = front;
        this.back = back;
        this.category = category;
        this.stage = 0;            // 新卡片从 stage 0 开始
        String today = LocalDate.now().toString();
        this.nextReviewDate = today; // 新建时立即可以复习
        this.createdDate = today;
        this.lastReviewDate = "";
        this.easyCount = 0;
        this.mediumCount = 0;
        this.hardCount = 0;
    }

    /**
     * 判断今天是否需要复习这张卡片
     * 【讲解重点：复习流程的触发条件】
     * 比较 nextReviewDate 和今天：
     *   如果 nextReviewDate <= today → 到期了，需要复习
     *   否则 → 还没到时间
     */
    public boolean isDue(LocalDate today) {
        if (nextReviewDate == null || nextReviewDate.isEmpty()) return false;
        return !LocalDate.parse(nextReviewDate).isAfter(today);
    }

    /** 掌握度百分比 (0%~100%)，stage 每升一级 +20% */
    public int getMasteryPercent() {
        return stage * 20;
    }

    // ----- getters & setters 所有字段都需要，Gson 依赖它们做序列化 -----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFront() { return front; }
    public void setFront(String front) { this.front = front; }

    public String getBack() { return back; }
    public void setBack(String back) { this.back = back; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getStage() { return stage; }
    public void setStage(int stage) { this.stage = stage; }

    public String getNextReviewDate() { return nextReviewDate; }
    public void setNextReviewDate(String nextReviewDate) { this.nextReviewDate = nextReviewDate; }

    public String getLastReviewDate() { return lastReviewDate; }
    public void setLastReviewDate(String lastReviewDate) { this.lastReviewDate = lastReviewDate; }

    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }

    public int getEasyCount() { return easyCount; }
    public void setEasyCount(int easyCount) { this.easyCount = easyCount; }

    public int getMediumCount() { return mediumCount; }
    public void setMediumCount(int mediumCount) { this.mediumCount = mediumCount; }

    public int getHardCount() { return hardCount; }
    public void setHardCount(int hardCount) { this.hardCount = hardCount; }

    /** 总复习次数 = 记得 + 模糊 + 不记得 */
    public int getTotalReviewCount() { return easyCount + mediumCount + hardCount; }

    public boolean isStarred() { return starred; }
    public void setStarred(boolean starred) { this.starred = starred; }
}
