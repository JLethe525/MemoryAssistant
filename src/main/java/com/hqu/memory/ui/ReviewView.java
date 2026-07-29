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
 * 复习页面：展示待复习卡片，用户看完正面 → 点"显示答案" → 评级
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

    private List<FlashCard> cards;
    private int currentIndex;
    private boolean isFlipped;

    public ReviewView() {
        progressLabel = new Label("准备开始");
        progressLabel.getStyleClass().add("stat-label");

        progressBar = new ProgressBar(0);
        progressBar.getStyleClass().add("glass-progress");
        progressBar.setPrefWidth(400);

        // 正反面显示
        frontArea = createReviewTextArea();
        backArea = createReviewTextArea();
        backSection = new VBox(8);
        backSection.setAlignment(Pos.CENTER);

        Label backLabel = new Label("— 答案 —");
        backLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.5); -fx-font-size: 14px;");
        backSection.getChildren().addAll(backLabel, backArea);

        // 卡片内容区域
        centerBox = new VBox(20);
        centerBox.setAlignment(Pos.CENTER);
        centerBox.setPadding(new Insets(20));
        centerBox.getStyleClass().add("review-card");
        centerBox.getChildren().addAll(frontArea);

        // "显示答案"按钮
        flipBtn = new Button("显示答案");
        flipBtn.getStyleClass().add("btn-primary");

        // 评级按钮
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

        // 顶部进度
        HBox topBar = new HBox(16);
        topBar.setAlignment(Pos.CENTER);
        topBar.setPadding(new Insets(0, 0, 20, 0));
        topBar.getChildren().addAll(progressLabel, progressBar);

        // 底部按钮区
        VBox bottomBox = new VBox(16);
        bottomBox.setAlignment(Pos.CENTER);
        bottomBox.setPadding(new Insets(24, 0, 0, 0));
        bottomBox.getChildren().addAll(flipBtn, rateButtons);

        view = new BorderPane();
        view.getStyleClass().add("glass-panel");
        view.setTop(topBar);
        view.setCenter(centerBox);
        view.setBottom(bottomBox);

        // 事件绑定
        flipBtn.setOnAction(e -> flipCard());
        hardBtn.setOnAction(e -> rateCard(SpacedRepetition.Difficulty.HARD));
        mediumBtn.setOnAction(e -> rateCard(SpacedRepetition.Difficulty.MEDIUM));
        easyBtn.setOnAction(e -> rateCard(SpacedRepetition.Difficulty.EASY));
    }

    public BorderPane getView() { return view; }

    /** 开始/刷新复习会话 */
    public void startReview() {
        LocalDate today = LocalDate.now();
        List<FlashCard> allCards = DataStore.loadCards();
        cards = allCards.stream()
                .filter(c -> c.isDue(today))
                .collect(Collectors.toList());
        currentIndex = 0;
        showCard();
    }

    // ---------- 私有方法 ----------

    private void showCard() {
        if (currentIndex >= cards.size()) {
            showComplete();
            return;
        }
        isFlipped = false;
        FlashCard card = cards.get(currentIndex);

        progressLabel.setText("第 " + (currentIndex + 1) + " 张 / 共 " + cards.size() + " 张");
        progressBar.setProgress((double) currentIndex / cards.size());

        frontArea.setText(card.getFront());
        backArea.setText(card.getBack());
        backSection.setVisible(false);
        centerBox.getChildren().setAll(frontArea);

        flipBtn.setVisible(true);
        rateButtons.setVisible(false);
    }

    private void flipCard() {
        isFlipped = true;
        centerBox.getChildren().setAll(frontArea, backSection);
        backSection.setVisible(true);
        flipBtn.setVisible(false);
        rateButtons.setVisible(true);
    }

    private void rateCard(SpacedRepetition.Difficulty diff) {
        if (currentIndex >= cards.size()) return;

        FlashCard card = cards.get(currentIndex);
        LocalDate today = LocalDate.now();

        // 记录评级次数
        switch (diff) {
            case EASY:   card.setEasyCount(card.getEasyCount() + 1);   break;
            case MEDIUM: card.setMediumCount(card.getMediumCount() + 1); break;
            case HARD:   card.setHardCount(card.getHardCount() + 1);   break;
        }

        SpacedRepetition.ReviewResult result = SpacedRepetition.calculate(card.getStage(), diff, today);
        card.setStage(result.newStage);
        card.setNextReviewDate(result.nextReviewDate.toString());
        card.setLastReviewDate(today.toString());

        // 写回数据文件
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
