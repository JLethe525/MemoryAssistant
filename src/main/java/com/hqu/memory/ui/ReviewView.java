package com.hqu.memory.ui;

import com.hqu.memory.model.FlashCard;
import com.hqu.memory.service.SpacedRepetition;
import com.hqu.memory.storage.DataStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 复习页面
 * 【讲解重点：复习的核心工作流程】
 *
 * 流程：
 * 1. startReview() → 从 DataStore 加载所有卡片，筛选出到期的
 * 2. showCard() → 显示当前卡片的正面（题目）
 * 3. flipCard() → 用户点击"显示答案"，翻转显示背面
 * 4. rateCard() → 用户评级（EASY/MEDIUM/HARD）
 * 5. 评级后立即更新 stage + 写盘，防止丢失进度
 * 6. 所有卡片完成 → showComplete() 显示总结
 */
public class ReviewView {

    private final BorderPane view;
    private final Label progressLabel;
    private final ProgressBar progressBar;
    private final TextArea frontArea;
    private final TextArea backArea;
    private final VBox backSection;
    private final Button flipBtn;
    private final HBox rateButtons;
    private final VBox centerBox;

    private List<FlashCard> cards;   // 待复习的卡片列表
    private int currentIndex;        // 当前第几张
    private boolean isFlipped;       // 是否已经翻转（是否已显示答案）

    public ReviewView() {
        // ... UI 初始化代码（略，重点是下面的业务逻辑）...
        progressLabel = new Label("准备开始");
        progressLabel.getStyleClass().add("stat-label");

        progressBar = new ProgressBar(0);
        progressBar.getStyleClass().add("glass-progress");
        progressBar.setPrefWidth(400);

        frontArea = createReviewTextArea();
        backArea = createReviewTextArea();
        backSection = new VBox(8);
        backSection.setAlignment(Pos.CENTER);

        Label backLabel = new Label("— 答案 —");
        backLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.5); -fx-font-size: 14px;");
        backSection.getChildren().addAll(backLabel, backArea);

        centerBox = new VBox(20);
        centerBox.setAlignment(Pos.CENTER);
        centerBox.setPadding(new Insets(20));
        centerBox.getStyleClass().add("review-card");
        centerBox.getChildren().addAll(frontArea);

        flipBtn = new Button("显示答案");
        flipBtn.getStyleClass().add("btn-primary");

        Button hardBtn = new Button("忘记 (HARD)");
        hardBtn.getStyleClass().addAll("btn-rate", "btn-rate-hard");

        Button mediumBtn = new Button("模糊 (MEDIUM)");
        mediumBtn.getStyleClass().addAll("btn-rate", "btn-rate-medium");

        Button easyBtn = new Button("记得 (EASY)");
        easyBtn.getStyleClass().addAll("btn-rate", "btn-rate-easy");

        rateButtons = new HBox(16);
        rateButtons.setAlignment(Pos.CENTER);
        rateButtons.getChildren().addAll(hardBtn, mediumBtn, easyBtn);
        rateButtons.setVisible(false);

        HBox topBar = new HBox(16);
        topBar.setAlignment(Pos.CENTER);
        topBar.setPadding(new Insets(0, 0, 20, 0));
        topBar.getChildren().addAll(progressLabel, progressBar);

        VBox bottomBox = new VBox(16);
        bottomBox.setAlignment(Pos.CENTER);
        bottomBox.setPadding(new Insets(24, 0, 0, 0));
        bottomBox.getChildren().addAll(flipBtn, rateButtons);

        view = new BorderPane();
        view.getStyleClass().add("glass-panel");
        view.setTop(topBar);
        view.setCenter(centerBox);
        view.setBottom(bottomBox);

        flipBtn.setOnAction(e -> flipCard());
        hardBtn.setOnAction(e -> rateCard(SpacedRepetition.Difficulty.HARD));
        mediumBtn.setOnAction(e -> rateCard(SpacedRepetition.Difficulty.MEDIUM));
        easyBtn.setOnAction(e -> rateCard(SpacedRepetition.Difficulty.EASY));
    }

    public BorderPane getView() { return view; }

    /**
     * 开始/刷新复习会话
     * 【讲解重点：筛选出今天到期的卡片】
     *
     * 1. 加载所有卡片
     * 2. 用 isDue(today) 筛选出到期的
     * 3. 从第一张开始展示
     */
    public void startReview() {
        LocalDate today = LocalDate.now();
        List<FlashCard> allCards = DataStore.loadCards();
        // 核心筛选：只复习到期的卡片
        cards = allCards.stream()
                .filter(c -> c.isDue(today))
                .collect(Collectors.toList());
        currentIndex = 0;
        showCard();
    }

    // ---------- 私有方法 ----------

    /** 显示当前卡片 */
    private void showCard() {
        if (currentIndex >= cards.size()) {
            showComplete();
            return;
        }
        isFlipped = false;
        FlashCard card = cards.get(currentIndex);

        // 更新进度显示
        progressLabel.setText("第 " + (currentIndex + 1) + " 张 / 共 " + cards.size() + " 张");
        progressBar.setProgress((double) currentIndex / cards.size());

        // 显示正面（题目）
        frontArea.setText(card.getFront());
        backArea.setText(card.getBack());
        backSection.setVisible(false);
        centerBox.getChildren().setAll(frontArea);

        // 隐藏 "显示答案" 按钮，等待用户主动点击
        flipBtn.setVisible(true);
        rateButtons.setVisible(false);
    }

    /** 翻转卡片，显示答案 */
    private void flipCard() {
        isFlipped = true;
        // 正面和答案同时显示
        centerBox.getChildren().setAll(frontArea, backSection);
        backSection.setVisible(true);
        flipBtn.setVisible(false);
        rateButtons.setVisible(true);
    }

    /**
     * 用户评级后的处理
     * 【讲解重点：更新 stage 并立即保存】
     *
     * 1. 根据用户评级计算新 stage 和下次复习日期
     * 2. 记录评级次数（用于统计展示）
     * 3. 立即写回文件（防止崩溃丢失进度）
     * 4. 跳到下一张
     */
    private void rateCard(SpacedRepetition.Difficulty diff) {
        if (currentIndex >= cards.size()) return;

        FlashCard card = cards.get(currentIndex);
        LocalDate today = LocalDate.now();

        // 记录评级次数（三种评级都算复习）
        switch (diff) {
            case EASY:   card.setEasyCount(card.getEasyCount() + 1);   break;
            case MEDIUM: card.setMediumCount(card.getMediumCount() + 1); break;
            case HARD:   card.setHardCount(card.getHardCount() + 1);   break;
        }

        // 核心算法：调用 SpacedRepetition.calculate()
        SpacedRepetition.ReviewResult result = SpacedRepetition.calculate(card.getStage(), diff, today);
        card.setStage(result.newStage);
        card.setNextReviewDate(result.nextReviewDate.toString());
        card.setLastReviewDate(today.toString());

        // 立即写盘保存
        List<FlashCard> allCards = DataStore.loadCards();
        for (int i = 0; i < allCards.size(); i++) {
            if (allCards.get(i).getId().equals(card.getId())) {
                allCards.set(i, card);
                break;
            }
        }
        DataStore.saveCards(allCards);

        currentIndex++;
        showCard();
    }

    /** 复习完成，显示总结 */
    private void showComplete() {
        long reviewedToday = DataStore.loadCards().stream()
                .filter(c -> c.getLastReviewDate().equals(LocalDate.now().toString()))
                .count();

        progressLabel.setText("复习完成！");
        progressBar.setProgress(1.0);

        frontArea.setText("🎉 本轮复习完成！\n\n共复习了 " + cards.size() + " 张卡片\n"
                + "今日累计复习: " + reviewedToday + " 张");

        backSection.setVisible(false);
        centerBox.getChildren().setAll(frontArea);
        flipBtn.setVisible(false);
        rateButtons.setVisible(false);
    }

    private TextArea createReviewTextArea() {
        TextArea ta = new TextArea();
        ta.setWrapText(true);
        ta.setEditable(false);
        ta.setPrefRowCount(6);
        ta.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 22px; "
                + "-fx-border-color: transparent; -fx-control-inner-background: transparent; "
                + "-fx-font-family: 'Microsoft YaHei', 'PingFang SC', 'Segoe UI', sans-serif; "
                + "-fx-font-weight: bold; -fx-text-alignment: center;");
        return ta;
    }
}
