package com.hqu.memory.ui;

import com.hqu.memory.model.FlashCard;
import com.hqu.memory.service.SpacedRepetition;
import com.hqu.memory.storage.DataStore;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
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
 * 每个学科一条独立曲线，彩色节点显示该学科卡片分布
 */
public class CurveView {

    private final BorderPane view;
    private final Canvas canvas;
    private final Label stageLabel;
    private double animOffset = 0;

    /** 学科配色（最多支持 8 个） */
    private static final String[] CAT_COLORS = {
            "#3b82f6", "#ef4444", "#22c55e", "#f59e0b",
            "#a855f7", "#ec4899", "#06b6d4", "#f97316"
    };

    public CurveView() {
        view = new BorderPane();
        view.getStyleClass().add("glass-panel");

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 16, 0));

        Label title = new Label("📈 各科遗忘曲线");
        title.getStyleClass().add("page-title");

        stageLabel = new Label();
        stageLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.5); -fx-font-size: 13px; -fx-padding: 0 0 0 16;");

        header.getChildren().addAll(title, stageLabel);

        canvas = new Canvas(860, 460);
        canvas.setEffect(new DropShadow(20, Color.rgb(59, 130, 246, 0.15)));

        StackPane canvasBox = new StackPane(canvas);
        canvasBox.setPadding(new Insets(0));

        Label hint = new Label("遗忘曲线：每条线代表一个学科的掌握趋势。圆点表示该阶段卡片数，越大张数越多。");
        hint.setStyle("-fx-text-fill: rgba(255,255,255,0.35); -fx-font-size: 12px; -fx-padding: 12 0 0 0;");
        hint.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        HBox hintBox = new HBox(hint);
        hintBox.setAlignment(Pos.CENTER);

        VBox bottomBox = new VBox(4);
        bottomBox.setAlignment(Pos.CENTER);
        bottomBox.getChildren().add(hintBox);

        view.setTop(header);
        view.setCenter(canvasBox);
        view.setBottom(bottomBox);

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
        long total = cards.size();
        stageLabel.setText("总卡片 " + total + " 张  ·  今日复习 " + reviewedToday + " 张");
    }

    private void draw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double W = canvas.getWidth();
        double H = canvas.getHeight();
        if (W < 100 || H < 100) return; // Canvas 还没初始化完，跳过

        g.clearRect(0, 0, W, H);

        // ===== 坐标轴 =====
        double padL = 70, padR = 30, padT = 30, padB = 60;
        double ax = padL, ay = padT;
        double aw = W - padL - padR, ah = H - padT - padB;

        // 网格线
        for (int i = 0; i <= 5; i++) {
            double y = ay + ah - (ah * i / 5.0);
            g.setStroke(Color.rgb(255, 255, 255, 0.06));
            g.strokeLine(ax, y, ax + aw, y);
            g.setFill(Color.rgb(255, 255, 255, 0.35));
            g.setFont(Font.font("Microsoft YaHei", 12));
            g.setTextAlign(TextAlignment.RIGHT);
            g.fillText((100 - i * 20) + "%", ax - 8, y + 4);
        }

        // X 轴
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

        // ===== 每条学科一条曲线 =====
        double lambda = 6.9078;
        double breathe = Math.sin(animOffset * 2) * 0.015;

        List<FlashCard> allCards = DataStore.loadCards();
        if (allCards == null) allCards = new ArrayList<>();
        Map<String, List<FlashCard>> grouped = allCards.stream()
                .collect(Collectors.groupingBy(FlashCard::getCategory));
        List<String> categories = new ArrayList<>(grouped.keySet());

        // 先画理论曲线（灰色虚线）作为对比基准
        g.setStroke(Color.rgb(255, 255, 255, 0.08));
        g.setLineDashes(4, 4);
        g.beginPath();
        boolean firstDash = true;
        for (int px = 0; px <= aw; px++) {
            double t = px / aw * 30;
            double retention = Math.exp(-t / lambda);
            double x = ax + px;
            double y = ay + ah - ah * retention;
            if (firstDash) { g.moveTo(x, y); firstDash = false; } else g.lineTo(x, y);
        }
        g.stroke();
        g.setLineDashes(null);

        for (int ci = 0; ci < categories.size(); ci++) {
            String cat = categories.get(ci);
            List<FlashCard> catCards = grouped.get(cat);
            Color color = Color.web(CAT_COLORS[ci % CAT_COLORS.length]);

            // ---- 为该学科计算"有效掌握率"曲线 ----
            // 按 stage 统计该学科卡片数
            Map<Integer, Long> stageCount = new HashMap<>();
            for (int s = 0; s <= 5; s++) stageCount.put(s, 0L);
            for (FlashCard c : catCards) {
                int s = c.getStage();
                stageCount.put(s, stageCount.getOrDefault(s, 0L) + 1);
            }
            long totalCat = catCards.size();

            // ---- 绘制曲线 ----
            g.beginPath();
            boolean firstPt = true;
            for (int px = 0; px <= aw; px++) {
                double t = px / aw * 30;
                double baseRetention = Math.exp(-t / lambda);
                // 加权：该学科的卡片分布影响整体记忆率
                double weightedRetention = 0;
                for (int s = 0; s <= 5; s++) {
                    long cnt = stageCount.getOrDefault(s, 0L);
                    if (cnt == 0) continue;
                    int day = SpacedRepetition.INTERVALS[s];
                    // 这个 stage 的卡片距离上次复习的遗忘程度
                    double cardRetention = Math.exp(-(t + day) / lambda);
                    weightedRetention += cardRetention * cnt / totalCat;
                }
                double retention = totalCat > 0 ? weightedRetention + breathe : baseRetention + breathe;
                double x = ax + px;
                double y = ay + ah - ah * retention;
                if (firstPt) { g.moveTo(x, y); firstPt = false; } else g.lineTo(x, y);
            }
            // 发光 + 主曲线
            Color glowColor = Color.rgb(
                (int)(color.getRed()*255), (int)(color.getGreen()*255), (int)(color.getBlue()*255), 0.3);
            g.setStroke(glowColor);
            g.setLineWidth(4);
            g.stroke();
            g.setStroke(color);
            g.setLineWidth(2.5);
            g.stroke();

            // ---- 在每个节点画该学科的气泡 ----
            for (int s = 0; s <= 5; s++) {
                int d = SpacedRepetition.INTERVALS[s];
                double t = d;
                double baseRet = Math.exp(-t / lambda);
                double ret = baseRet + breathe;
                double nx = ax + aw * d / 30.0;
                double ny = ay + ah - ah * ret;

                long cnt = stageCount.getOrDefault(s, 0L);
                if (cnt == 0) continue;

                // 气泡大小
                double radius = 3 + Math.min(cnt * 2.5, 18);

                // 发光圈
                double aR = color.getRed(), aG = color.getGreen(), aB = color.getBlue();
                RadialGradient nodeGlow = new RadialGradient(0, 0, 0.5, 0.5, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.rgb((int)(aR*255), (int)(aG*255), (int)(aB*255), 0.25)),
                        new Stop(1, Color.rgb((int)(aR*255), (int)(aG*255), (int)(aB*255), 0)));
                g.setFill(nodeGlow);
                g.fillOval(nx - radius - 5, ny - radius - 5, (radius + 5) * 2, (radius + 5) * 2);

                // 半透明气泡
                g.setFill(Color.rgb((int)(aR*255), (int)(aG*255), (int)(aB*255), 0.45));
                g.fillOval(nx - radius, ny - radius, radius * 2, radius * 2);
                g.setStroke(Color.rgb((int)(aR*255), (int)(aG*255), (int)(aB*255), 0.75));
                g.setLineWidth(1.5);
                g.strokeOval(nx - radius, ny - radius, radius * 2, radius * 2);

                // 气泡内的数字
                if (radius >= 6) {
                    g.setFill(Color.rgb(255, 255, 255, 0.9));
                    g.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, (int)Math.min(radius + 2, 14)));
                    g.setTextAlign(TextAlignment.CENTER);
                    g.fillText(String.valueOf(cnt), nx, ny + 4);
                }
            }

            // ---- 曲线末端标注学科名 ----
            double labelX = ax + aw + 6;
            double labelY = ay + ah - ah * (Math.exp(-30 / lambda) + breathe);
            g.setFill(color);
            g.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
            g.setTextAlign(TextAlignment.LEFT);
            g.fillText(cat, labelX, labelY + 4);
        }

        // ---- 底部图例 ----
        drawLegend(g, W, H, categories);
    }

    private void drawLegend(GraphicsContext g, double W, double H, List<String> categories) {
        if (categories.isEmpty()) {
            g.setFill(Color.rgb(255, 255, 255, 0.3));
            g.setFont(Font.font("Microsoft YaHei", 12));
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText("暂无数据，添加卡片后会自动生成各学科曲线", W / 2, H - 16);
            return;
        }

        double y = H - 16;
        int total = categories.size();
        // 计算每个图例项的宽度
        double itemWidth = Math.min(140, (W - 100) / total);

        for (int i = 0; i < total; i++) {
            Color color = Color.web(CAT_COLORS[i % CAT_COLORS.length]);

            double centerX = W / 2;
            double startX = centerX - (total * itemWidth) / 2 + i * itemWidth + itemWidth / 2;

            // 色块
            g.setFill(color);
            g.fillRoundRect(startX - 40, y - 10, 10, 10, 2, 2);

            // 学科名
            g.setFill(Color.rgb(255, 255, 255, 0.6));
            g.setFont(Font.font("Microsoft YaHei", 11));
            g.setTextAlign(TextAlignment.LEFT);
            g.fillText(categories.get(i), startX - 25, y + 1);
        }

        // 灰色虚线说明
        g.setFill(Color.rgb(255, 255, 255, 0.2));
        g.setFont(Font.font("Microsoft YaHei", 10));
        g.setTextAlign(TextAlignment.RIGHT);
        g.fillText("--- 理论曲线", W - 10, H - 30);
    }
}
