package com.hqu.memory.ui;

import com.hqu.memory.model.FlashCard;
import com.hqu.memory.storage.DataStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 首页仪表盘：概览今日待复习卡片、各类别进度、快捷进入复习
 */
public class HomeView {

    private final BorderPane view;
    private final Label dueCountLabel;
    private final Label reviewedCountLabel;
    private final VBox categoryBox;
    private final VBox centerContent;
    private Runnable onStartReview;

    public HomeView() {
        view = new BorderPane();
        view.getStyleClass().add("glass-panel");

        // 顶部欢迎语
        Label welcome = new Label("📚 考研记忆助手");
        welcome.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: white;");

        // 统计数字行
        HBox statsRow = new HBox(30);
        statsRow.setAlignment(Pos.CENTER);
        statsRow.setPadding(new Insets(20, 0, 0, 0));

        // 待复习
        VBox dueBox = createStatBox("待复习", dueCountLabel = new Label("0"), "#f5576c");
        // 今日已复习
        VBox reviewedBox = createStatBox("已复习", reviewedCountLabel = new Label("0"), "#43e97b");

        statsRow.getChildren().addAll(dueBox, reviewedBox);

        // 分类进度区域
        Label sectionTitle = new Label("各科目掌握进度");
        sectionTitle.setStyle("-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 14px;");

        categoryBox = new VBox(10);
        categoryBox.setPadding(new Insets(10, 0, 0, 0));

        centerContent = new VBox(12);
        centerContent.getChildren().addAll(sectionTitle, categoryBox);

        // 底部按钮
        Button startBtn = new Button("开始今日复习");
        startBtn.getStyleClass().add("btn-primary");
        startBtn.setMaxWidth(300);
        startBtn.setOnAction(e -> { if (onStartReview != null) onStartReview.run(); });

        HBox btnBox = new HBox(startBtn);
        btnBox.setAlignment(Pos.CENTER);
        btnBox.setPadding(new Insets(20, 0, 0, 0));

        VBox topSection = new VBox(8);
        topSection.setAlignment(Pos.CENTER);
        topSection.getChildren().addAll(welcome, statsRow);

        // 用 ScrollPane 包裹中间内容
        ScrollPane scrollPane = new ScrollPane(centerContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(300);

        view.setTop(topSection);
        view.setCenter(scrollPane);
        view.setBottom(btnBox);
    }

    public BorderPane getView() { return view; }

    public void setOnStartReview(Runnable r) { this.onStartReview = r; }

    /** 刷新首页数据 */
    public void refresh() {
        List<FlashCard> cards = DataStore.loadCards();
        LocalDate today = LocalDate.now();

        long due = cards.stream().filter(c -> c.isDue(today)).count();
        long reviewed = cards.stream()
                .filter(c -> today.toString().equals(c.getLastReviewDate()))
                .count();

        dueCountLabel.setText(String.valueOf(due));
        reviewedCountLabel.setText(String.valueOf(reviewed));

        // 按分类汇总
        categoryBox.getChildren().clear();
        Map<String, List<FlashCard>> grouped = cards.stream()
                .collect(Collectors.groupingBy(FlashCard::getCategory));

        grouped.forEach((cat, catCards) -> {
            double avg = catCards.stream().mapToInt(FlashCard::getStage).average().orElse(0);
            int pct = (int) (avg / 5.0 * 100);
            long catDue = catCards.stream().filter(c -> c.isDue(today)).count();

            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);

            Label name = new Label(cat);
            name.setStyle("-fx-text-fill: rgba(255,255,255,0.85); -fx-font-size: 13px; -fx-min-width: 65;");

            ProgressBar pb = new ProgressBar(pct / 100.0);
            pb.getStyleClass().add("glass-progress");
            pb.setPrefWidth(200);

            Label pctLabel = new Label(pct + "%");
            pctLabel.setStyle("-fx-text-fill: " + (pct >= 80 ? "#43e97b" : pct >= 40 ? "#fda085" : "#f5576c")
                    + "; -fx-font-size: 13px; -fx-font-weight: bold;");

            Label dueBadge = new Label();
            if (catDue > 0) {
                dueBadge.setText(catDue + " 待复习");
                dueBadge.setStyle("-fx-text-fill: #f5576c; -fx-font-size: 12px;");
            } else {
                dueBadge.setText("✓ 已复习");
                dueBadge.setStyle("-fx-text-fill: #43e97b; -fx-font-size: 12px;");
            }

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            row.getChildren().addAll(name, pb, pctLabel, spacer, dueBadge);
            categoryBox.getChildren().add(row);
        });
    }

    private VBox createStatBox(String label, Label valueLabel, String color) {
        VBox box = new VBox(4);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("glass-panel-light");
        box.setPrefWidth(160);
        box.setPadding(new Insets(16));

        Label title = new Label(label);
        title.getStyleClass().add("stat-label");

        valueLabel.getStyleClass().add("stat-number");
        valueLabel.setTextFill(Color.web(color));

        box.getChildren().addAll(title, valueLabel);
        return box;
    }
}
