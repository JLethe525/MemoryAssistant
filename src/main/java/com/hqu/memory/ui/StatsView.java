package com.hqu.memory.ui;

import com.hqu.memory.model.FlashCard;
import com.hqu.memory.storage.DataStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 统计页面：展示总卡片数、掌握数、连续学习天数、分类掌握率等
 */
public class StatsView {

    private final BorderPane view;
    private final Label totalLabel;
    private final Label masteredLabel;
    private final Label dueLabel;
    private final Label streakLabel;
    private final VBox categoryMasteryBox;

    public StatsView() {
        view = new BorderPane();
        view.getStyleClass().add("glass-panel");

        // 标题
        Label title = new Label("学习统计");
        title.getStyleClass().add("page-title");
        VBox titleBox = new VBox(title);
        titleBox.setPadding(new Insets(0, 0, 20, 0));

        // 概览数字卡片行
        HBox overviewRow = new HBox(20);
        overviewRow.setAlignment(Pos.CENTER);
        overviewRow.setPadding(new Insets(0, 0, 24, 0));

        totalLabel = new Label("0");
        masteredLabel = new Label("0");
        dueLabel = new Label("0");
        streakLabel = new Label("0");

        overviewRow.getChildren().addAll(
                createOverviewCard("总卡片", totalLabel, "#667eea"),
                createOverviewCard("已掌握", masteredLabel, "#43e97b"),
                createOverviewCard("待复习", dueLabel, "#f5576c"),
                createOverviewCard("连续学习", streakLabel, "#f6d365")
        );

        // 分类掌握率
        Label sectionTitle = new Label("各科目掌握程度");
        sectionTitle.setStyle("-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 14px; -fx-padding: 0 0 8 0;");

        categoryMasteryBox = new VBox(10);

        VBox centerContent = new VBox(12);
        centerContent.getChildren().addAll(sectionTitle, categoryMasteryBox);

        ScrollPane scrollPane = new ScrollPane(centerContent);
        scrollPane.setFitToWidth(true);

        VBox topSection = new VBox(8);
        topSection.getChildren().addAll(titleBox, overviewRow);

        view.setTop(topSection);
        view.setCenter(scrollPane);
    }

    public BorderPane getView() { return view; }

    /** 刷新统计数据 */
    public void refresh() {
        List<FlashCard> cards = DataStore.loadCards();
        LocalDate today = LocalDate.now();

        int total = cards.size();
        long mastered = cards.stream().filter(c -> c.getStage() == 5).count();
        long due = cards.stream().filter(c -> c.isDue(today)).count();
        long streak = calculateStreak(cards, today);

        totalLabel.setText(String.valueOf(total));
        masteredLabel.setText(String.valueOf(mastered));
        dueLabel.setText(String.valueOf(due));
        streakLabel.setText(streak + " 天");

        // 分类掌握率
        categoryMasteryBox.getChildren().clear();
        Map<String, List<FlashCard>> grouped = cards.stream()
                .collect(Collectors.groupingBy(FlashCard::getCategory));

        if (grouped.isEmpty()) {
            Label empty = new Label("暂无数据，先去添加卡片吧");
            empty.setStyle("-fx-text-fill: rgba(255,255,255,0.4); -fx-font-size: 14px;");
            categoryMasteryBox.getChildren().add(empty);
            return;
        }

        grouped.forEach((cat, catCards) -> {
            double avgStage = catCards.stream().mapToInt(FlashCard::getStage).average().orElse(0);
            int pct = (int) (avgStage / 5.0 * 100);

            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);

            Label name = new Label(cat);
            name.setStyle("-fx-text-fill: rgba(255,255,255,0.85); -fx-font-size: 14px; -fx-min-width: 70;");

            ProgressBar pb = new ProgressBar(Math.min(pct / 100.0, 1.0));
            pb.getStyleClass().add("glass-progress");
            pb.setPrefWidth(250);

            Label pctLabel = new Label(pct + "%");
            pctLabel.setStyle("-fx-text-fill: " + getColorForPercent(pct) + "; -fx-font-size: 14px; -fx-font-weight: bold;");

            Label countLabel = new Label(catCards.size() + " 张");
            countLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.4); -fx-font-size: 12px;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            row.getChildren().addAll(name, pb, pctLabel, spacer, countLabel);
            categoryMasteryBox.getChildren().add(row);
        });
    }

    /** 计算连续学习天数 */
    private long calculateStreak(List<FlashCard> cards, LocalDate today) {
        Set<LocalDate> reviewDates = cards.stream()
                .map(FlashCard::getLastReviewDate)
                .filter(d -> d != null && !d.isEmpty())
                .map(LocalDate::parse)
                .collect(Collectors.toSet());

        long streak = 0;
        LocalDate check = today;
        while (reviewDates.contains(check)) {
            streak++;
            check = check.minusDays(1);
        }
        return streak;
    }

    private VBox createOverviewCard(String label, Label valueLabel, String color) {
        VBox box = new VBox(6);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("glass-panel-light");
        box.setPrefWidth(140);
        box.setPadding(new Insets(16));

        Label title = new Label(label);
        title.getStyleClass().add("stat-label");

        valueLabel.getStyleClass().add("stat-number");
        valueLabel.setTextFill(Color.web(color));

        box.getChildren().addAll(title, valueLabel);
        return box;
    }

    private String getColorForPercent(int pct) {
        if (pct >= 80) return "#43e97b";
        if (pct >= 50) return "#fda085";
        if (pct >= 20) return "#f6d365";
        return "#f5576c";
    }
}
