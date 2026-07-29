package com.hqu.memory.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 闪卡数据模型
 * stage: 0-5, 对应遗忘曲线间隔 [0,1,3,7,15,30] 天
 * stage=5 表示"已掌握"
 */
public class FlashCard {
    private String id;
    private String front;       // 正面（题目）
    private String back;        // 背面（答案）
    private String category;    // 分类（政治/英语/数学/专业课等）
    private int stage;          // 记忆阶段 0-5
    private String nextReviewDate;  // 下次复习日期 (ISO: yyyy-MM-dd)
    private String lastReviewDate;  // 上次复习日期
    private String createdDate;     // 创建日期

    // Gson 反序列化需要无参构造
    public FlashCard() {}

    public FlashCard(String front, String back, String category) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.front = front;
        this.back = back;
        this.category = category;
        this.stage = 0;
        String today = LocalDate.now().toString();
        this.nextReviewDate = today;
        this.createdDate = today;
        this.lastReviewDate = "";
    }

    /** 判断今天是否需要复习这张卡片 */
    public boolean isDue(LocalDate today) {
        if (nextReviewDate == null || nextReviewDate.isEmpty()) return false;
        return !LocalDate.parse(nextReviewDate).isAfter(today);
    }

    /** 掌握度百分比 (0%~100%) */
    public int getMasteryPercent() {
        return stage * 20;
    }

    // ----- getters & setters -----

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
}
