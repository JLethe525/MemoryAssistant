package com.hqu.memory.ui;

import com.hqu.memory.model.FlashCard;
import com.hqu.memory.service.SpacedRepetition;
import com.hqu.memory.storage.DataStore;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 遗忘曲线可视化页面
 * 一次显示一个学科，底部按钮切换学科
 */
public class CurveView {

    private final BorderPane view;
    private final Canvas canvas;
    private final Label stageLabel;
    private final HBox tabBar;
    private double animOffset = 0;

    private String selectedCategory = null;
    private List<String> categories = new ArrayList<>();

    private static final String[] CAT_COLORS = {
            "#3b82f6", "#ef4444", "#22c55e", "#f59e0b",
            "#a855f7", "#ec4899", "#06b6d4", "#f97316"
    };

    public CurveView() {
        view = new BorderPane();
        view.getStyleClass().add("glass-panel");

        // 标题
        Label title = new Label("📈 遗忘曲线");
        title.getStyleClass().add("page-title");

        stageLabel = new Label();
        stageLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.5); -fx-font-size: 13px; -fx-padding: 0 0 0 16;");

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 16, 0));
        header.getChildren().addAll(title, stageLabel);

        // Canvas
        canvas = new Canvas(860, 440);
        canvas.setEffect(new DropShadow(20, Color.rgb(59, 130, 246, 0.15)));
        StackPane canvasBox = new StackPane(canvas);
        canvasBox.setPadding(new Insets(0));
        VBox.setVgrow(canvasBox, Priority.ALWAYS);

        // 学科切换栏
        tabBar = new HBox(8);
        tabBar.setAlignment(Pos.CENTER);
        tabBar.setPadding(new Insets(12, 0, 0, 0));

        view.setTop(header);
        view.setCenter(canvasBox);
        view.setBottom(tabBar);

        AnimationTimer timer = new AnimationTimer() {
            long start = 0;
            @Override
            public void handle(long now) {
                if (start == 0) start = now;
                animOffset = (now - start) / 1e9 * 0.3;
                draw();
            }
        };
        timer.start();
    }

    public BorderPane getView() { return view; }

    public void refresh() {
        List<FlashCard> cards = DataStore.loadCards();
        long reviewedToday = cards.stream()
                .filter(c -> LocalDate.now().toString().equals(c.getLastReviewDate()))
                .count();
        stageLabel.setText("总卡片 " + cards.size() + " 张 · 今日复习 " + reviewedToday + " 张");

        // 更新学科列表
        categories = cards.stream().map(FlashCard::getCategory).distinct().sorted().collect(Collectors.toList());

        tabBar.getChildren().clear();

        // "全部"按钮
        Button allBtn = createTabBtn("全部", selectedCategory == null);
        allBtn.setOnAction(e -> { selectedCategory = null; refreshTabs(); });
        tabBar.getChildren().add(allBtn);

        for (String cat : categories) {
            long count = cards.stream().filter(c -> c.getCategory().equals(cat)).count();
            Button btn = createTabBtn(cat + "(" + count + ")", cat.equals(selectedCategory));
            String finalCat = cat;
            btn.setOnAction(e -> { selectedCategory = finalCat; refreshTabs(); });
            tabBar.getChildren().add(btn);
        }
    }

    private Button createTabBtn(String text, boolean active) {
        Button btn = new Button(text);
        if (active) {
            btn.setStyle("-fx-background-color: rgba(59,130,246,0.3); -fx-background-radius: 10; "
                    + "-fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 6 14; -fx-cursor: hand; "
                    + "-fx-border-color: rgba(59,130,246,0.5); -fx-border-radius: 10; -fx-font-weight: bold;");
        } else {
            btn.setStyle("-fx-background-color: rgba(255,255,255,0.06); -fx-background-radius: 10; "
                    + "-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 12px; -fx-padding: 6 14; -fx-cursor: hand; "
                    + "-fx-border-color: rgba(255,255,255,0.08); -fx-border-radius: 10;");
        }
        return btn;
    }

    private void refreshTabs() {
        List<FlashCard> cards = DataStore.loadCards();
        categories = cards.stream().map(FlashCard::getCategory).distinct().sorted().collect(Collectors.toList());

        tabBar.getChildren().clear();
        Button allBtn = createTabBtn("全部", selectedCategory == null);
        allBtn.setOnAction(e -> { selectedCategory = null; refreshTabs(); });
        tabBar.getChildren().add(allBtn);

        for (String cat : categories) {
            long count = cards.stream().filter(c -> c.getCategory().equals(cat)).count();
            Button btn = createTabBtn(cat + "(" + count + ")", cat.equals(selectedCategory));
            String finalCat = cat;
            btn.setOnAction(e -> { selectedCategory = finalCat; refreshTabs(); });
            tabBar.getChildren().add(btn);
        }
    }

    private void draw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double W = canvas.getWidth();
        double H = canvas.getHeight();
        if (W < 100 || H < 100) return;

        g.clearRect(0, 0, W, H);

        // 坐标轴
        double padL = 70, padR = 30, padT = 30, padB = 40;
        double ax = padL, ay = padT;
        double aw = W - padL - padR, ah = H - padT - padB;

        for (int i = 0; i <= 5; i++) {
            double y = ay + ah - (ah * i / 5.0);
            g.setStroke(Color.rgb(255, 255, 255, 0.06));
            g.strokeLine(ax, y, ax + aw, y);
            g.setFill(Color.rgb(255, 255, 255, 0.35));
            g.setFont(Font.font("Microsoft YaHei", 12));
            g.setTextAlign(TextAlignment.RIGHT);
            g.fillText((100 - i * 20) + "%", ax - 8, y + 4);
        }

        int[] days = {0, 1, 3, 7, 15, 30};
        for (int d : days) {
            double x = ax + aw * d / 30.0;
            g.setFill(Color.rgb(255, 255, 255, 0.35));
            g.setFont(Font.font("Microsoft YaHei", 12));
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText("第" + d + "天", x, ay + ah + 20);
            g.setStroke(Color.rgb(255, 255, 255, 0.08));
            g.strokeLine(x, ay, x, ay + ah);
        }
        g.setStroke(Color.rgb(255, 255, 255, 0.2));
        g.setLineWidth(1.5);
        g.strokeLine(ax, ay, ax, ay + ah);
        g.strokeLine(ax, ay + ah, ax + aw, ay + ah);

        g.save();
        g.translate(12, ay + ah / 2);
        g.rotate(-90);
        g.setFill(Color.rgb(255, 255, 255, 0.25));
        g.setFont(Font.font("Microsoft YaHei", 12));
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText("记忆保留率", 0, 0);
        g.restore();

        // 数据
        double lambda = 6.9078;
        double breathe = Math.sin(animOffset * 2) * 0.015;

        List<FlashCard> allCards = DataStore.loadCards();
        if (allCards == null) allCards = new ArrayList<>();

        // 筛选数据
        List<FlashCard> displayCards = allCards;
        String displayName = "全部";
        if (selectedCategory != null) {
            String finalCat = selectedCategory;
            displayCards = allCards.stream().filter(c -> c.getCategory().equals(finalCat)).collect(Collectors.toList());
            displayName = selectedCategory;
        }

        // 理论曲线
        g.setStroke(Color.rgb(255, 255, 255, 0.08));
        g.setLineDashes(4, 4);
        g.beginPath();
        boolean firstDash = true;
        for (int px = 0; px <= aw; px++) {
            double t = px / aw * 30;
            double retention = Math.exp(-t / lambda);
            double x = ax + px, y = ay + ah - ah * retention;
            if (firstDash) { g.moveTo(x, y); firstDash = false; } else g.lineTo(x, y);
        }
        g.stroke();
        g.setLineDashes(null);

        // 按学科分组绘制
        Map<String, List<FlashCard>> grouped = displayCards.stream()
                .collect(Collectors.groupingBy(FlashCard::getCategory));

        int colorIdx = 0;
        for (var entry : grouped.entrySet()) {
            String cat = entry.getKey();
            List<FlashCard> catCards = entry.getValue();
            Color color = Color.web(CAT_COLORS[colorIdx % CAT_COLORS.length]);
            colorIdx++;

            // 按 stage 统计
            Map<Integer, Long> stageCount = new HashMap<>();
            for (int s = 0; s <= 5; s++) stageCount.put(s, 0L);
            for (FlashCard c : catCards) stageCount.put(c.getStage(), stageCount.get(c.getStage()) + 1);
            long totalCat = catCards.size();

            // 画曲线
            g.beginPath();
            boolean firstPt = true;
            for (int px = 0; px <= aw; px++) {
                double t = px / aw * 30;
                double base = Math.exp(-t / lambda);
                double weighted = 0;
                for (int s = 0; s <= 5; s++) {
                    long cnt = stageCount.getOrDefault(s, 0L);
                    if (cnt == 0) continue;
                    weighted += Math.exp(-(t + SpacedRepetition.INTERVALS[s]) / lambda) * cnt / totalCat;
                }
                double ret = (totalCat > 0 ? weighted : base) + breathe;
                double x = ax + px, y = ay + ah - ah * ret;
                if (firstPt) { g.moveTo(x, y); firstPt = false; } else g.lineTo(x, y);
            }
            Color gc = Color.rgb((int)(color.getRed()*255), (int)(color.getGreen()*255), (int)(color.getBlue()*255), 0.3);
            g.setStroke(gc); g.setLineWidth(4); g.stroke();
            g.setStroke(color); g.setLineWidth(2.5); g.stroke();

            // 节点气泡
            boolean dense = totalCat > 10;
            for (int s = 0; s <= 5; s++) {
                int d = SpacedRepetition.INTERVALS[s];
                double t = d;
                double base2 = Math.exp(-t / lambda);
                double w2 = 0;
                for (int st = 0; st <= 5; st++) {
                    long cnt = stageCount.getOrDefault(st, 0L);
                    if (cnt == 0) continue;
                    w2 += Math.exp(-(t + SpacedRepetition.INTERVALS[st]) / lambda) * cnt / totalCat;
                }
                double ret2 = (totalCat > 0 ? w2 : base2) + breathe;
                double nx = ax + aw * d / 30.0;
                double ny = ay + ah - ah * ret2;
                long cnt = stageCount.getOrDefault(s, 0L);
                if (cnt == 0) continue;

                double aR = color.getRed(), aG = color.getGreen(), aB = color.getBlue();
                if (dense) {
                    g.setFill(color);
                    g.fillOval(nx - 3, ny - 3, 6, 6);
                    g.setFill(Color.rgb(255, 255, 255, 0.7));
                    g.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 11));
                    g.setTextAlign(TextAlignment.CENTER);
                    g.fillText(cnt + "张", nx, ny - 10);
                } else {
                    double radius = 3 + Math.min(cnt * 2.5, 18);
                    RadialGradient glow = new RadialGradient(0, 0, 0.5, 0.5, 1, true, CycleMethod.NO_CYCLE,
                            new Stop(0, Color.rgb((int)(aR*255), (int)(aG*255), (int)(aB*255), 0.25)),
                            new Stop(1, Color.rgb((int)(aR*255), (int)(aG*255), (int)(aB*255), 0)));
                    g.setFill(glow);
                    g.fillOval(nx - radius - 5, ny - radius - 5, (radius + 5) * 2, (radius + 5) * 2);
                    g.setFill(Color.rgb((int)(aR*255), (int)(aG*255), (int)(aB*255), 0.45));
                    g.fillOval(nx - radius, ny - radius, radius * 2, radius * 2);
                    g.setStroke(Color.rgb((int)(aR*255), (int)(aG*255), (int)(aB*255), 0.75));
                    g.setLineWidth(1.5);
                    g.strokeOval(nx - radius, ny - radius, radius * 2, radius * 2);
                    if (radius >= 6) {
                        g.setFill(Color.rgb(255, 255, 255, 0.9));
                        g.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, (int)Math.min(radius + 2, 14)));
                        g.setTextAlign(TextAlignment.CENTER);
                        g.fillText(String.valueOf(cnt), nx, ny + 4);
                    }
                }
            }

            // 标签
            if (grouped.size() > 1 || selectedCategory == null) {
                double lx = ax + aw + 6;
                double lt = 30;
                double lb = Math.exp(-lt / lambda);
                double lw = 0;
                for (int st = 0; st <= 5; st++) {
                    long c = stageCount.getOrDefault(st, 0L);
                    if (c == 0) continue;
                    lw += Math.exp(-(lt + SpacedRepetition.INTERVALS[st]) / lambda) * c / totalCat;
                }
                double lr = (totalCat > 0 ? lw : lb) + breathe;
                double ly = ay + ah - ah * lr;
                g.setFill(color);
                g.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
                g.setTextAlign(TextAlignment.LEFT);
                g.fillText(cat, lx, ly + 4);
            }
        }

        if (grouped.isEmpty()) {
            g.setFill(Color.rgb(255, 255, 255, 0.3));
            g.setFont(Font.font("Microsoft YaHei", 12));
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText("暂无数据，添加卡片后自动生成遗忘曲线", W / 2, H / 2);
        }
    }
}
