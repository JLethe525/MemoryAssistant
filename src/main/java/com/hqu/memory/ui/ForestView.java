package com.hqu.memory.ui;

import com.hqu.memory.model.FlashCard;
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
import java.util.List;
import java.util.Random;
import java.util.prefs.Preferences;

/**
 * 记忆森林
 * 复习卡片→浇水→树长大
 * 完成番茄钟→长新树
 * 柔和手绘风格
 */
public class ForestView {

    private final BorderPane view;
    private final Canvas canvas;
    private final Label titleLabel;
    private final Label statsLabel;
    private double time = 0;

    private static final Preferences PREFS = Preferences.userNodeForPackage(ForestView.class);

    public ForestView() {
        view = new BorderPane();
        view.getStyleClass().add("glass-panel");

        // 标题
        titleLabel = new Label("🌳 记忆森林");
        titleLabel.getStyleClass().add("page-title");

        statsLabel = new Label();
        statsLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.4); -fx-font-size: 12px; -fx-padding: 0 0 0 12;");

        HBox header = new HBox(0);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 10, 0));
        header.getChildren().addAll(titleLabel, statsLabel);

        // Canvas
        canvas = new Canvas(860, 440);
        canvas.setEffect(new DropShadow(20, Color.rgb(34, 197, 94, 0.1)));

        StackPane canvasBox = new StackPane(canvas);
        canvasBox.setPadding(new Insets(0));
        VBox.setVgrow(canvasBox, Priority.ALWAYS);

        // 底部说明
        Label hint = new Label("📝 复习卡片为树木浇水  ·  🍅 完成番茄钟长出新的树苗");
        hint.setStyle("-fx-text-fill: rgba(255,255,255,0.25); -fx-font-size: 12px;");
        HBox hintBox = new HBox(hint);
        hintBox.setAlignment(Pos.CENTER);
        hintBox.setPadding(new Insets(12, 0, 0, 0));

        view.setTop(header);
        view.setCenter(canvasBox);
        view.setBottom(hintBox);

        // 呼吸动画
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                time += 0.008;
                draw();
            }
        };
        timer.start();
    }

    public BorderPane getView() { return view; }

    public void refresh() {
        statsLabel.setText(getStats());
    }

    private String getStats() {
        List<FlashCard> cards = DataStore.loadCards();
        long reviewed = cards.stream()
                .filter(c -> LocalDate.now().toString().equals(c.getLastReviewDate()))
                .count();
        long total = cards.size();
        int pomos = PREFS.getInt("pomo_" + LocalDate.now().toString(), 0);
        return "🌲 " + total + "张卡片  ·  💧 今日复习" + reviewed + "张  ·  🍅 " + pomos + "个番茄钟";
    }

    private void draw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double W = canvas.getWidth();
        double H = canvas.getHeight();
        if (W < 100 || H < 100) return;

        g.clearRect(0, 0, W, H);

        // ===== 背景：天空渐变 =====
        LinearGradient sky = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(15, 15, 40, 0.6)),
                new Stop(0.4, Color.rgb(20, 25, 50, 0.3)),
                new Stop(1, Color.rgb(10, 15, 20, 0.2)));
        g.setFill(sky);
        g.fillRect(0, 0, W, H * 0.65);

        // ===== 地面 =====
        LinearGradient ground = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(20, 40, 25, 0.5)),
                new Stop(1, Color.rgb(15, 30, 20, 0.3)));
        g.setFill(ground);
        g.fillRect(0, H * 0.65, W, H * 0.35);

        // 地面线（柔和曲线）
        g.setStroke(Color.rgb(34, 197, 94, 0.15));
        g.setLineWidth(2);
        g.beginPath();
        double groundY = H * 0.65;
        for (int px = 0; px <= W; px++) {
            double wave = Math.sin(px * 0.008 + time * 0.3) * 4
                       + Math.sin(px * 0.02 + time * 0.2) * 2;
            if (px == 0) g.moveTo(px, groundY + wave);
            else g.lineTo(px, groundY + wave);
        }
        g.stroke();

        // ===== 星星（微闪） =====
        Random rnd = new Random(42);
        for (int i = 0; i < 40; i++) {
            double sx = rnd.nextDouble() * W;
            double sy = rnd.nextDouble() * H * 0.5;
            double twinkle = 0.15 + 0.15 * Math.sin(time * 1.5 + i * 2.3);
            g.setFill(Color.rgb(255, 255, 255, twinkle));
            g.fillOval(sx, sy, 1.5, 1.5);
        }

        // ===== 数据 =====
        List<FlashCard> cards = DataStore.loadCards();
        int pomos = PREFS.getInt("pomo_" + LocalDate.now().toString(), 0);

        // ===== 种树 =====
        // 策略：每张卡片 = 一滴水，每完成 5 次复习 = 树升一级
        // 番茄钟 = 新树苗
        // 用固定随机种子的位置，让树的位置保持稳定

        // 收集所有卡片在每个 stage 的分布
        int[] stageCounts = new int[6];
        for (FlashCard c : cards) stageCounts[c.getStage()]++;

        // 树列表：每棵树有 (x, y, size)
        int totalTreeCount = Math.max(pomos, 1) + stageCounts[5] / 3 + (int)(cards.size() / 10);
        if (totalTreeCount > 30) totalTreeCount = 30;

        Random treeRnd = new Random(123);
        double[] treeX = new double[totalTreeCount];
        double[] treeSize = new double[totalTreeCount];
        double[] treeHue = new double[totalTreeCount]; // 颜色微调

        for (int i = 0; i < totalTreeCount; i++) {
            treeX[i] = 30 + treeRnd.nextDouble() * (W - 60);
            // 大小由番茄钟数量 + 复习卡片数决定
            double base = 0.3;
            double fromPomo = Math.min(pomos, 10) / 10.0 * 0.4;
            double fromCards = Math.min(cards.size(), 50) / 50.0 * 0.3;
            treeSize[i] = base + fromPomo + fromCards + treeRnd.nextDouble() * 0.15;
            if (treeSize[i] > 1.0) treeSize[i] = 1.0;
            treeHue[i] = 100 + treeRnd.nextDouble() * 40; // 不同绿色
        }

        // 从右到左画（远处先画）
        for (int i = totalTreeCount - 1; i >= 0; i--) {
            drawTree(g, treeX[i], groundY, treeSize[i], treeHue[i]);
        }

        // ===== 没有数据时的提示 =====
        if (cards.isEmpty() && pomos == 0) {
            g.setFill(Color.rgb(255, 255, 255, 0.2));
            g.setFont(Font.font("Microsoft YaHei", 14));
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText("🌱 开始学习和复习，你的森林会在这里生长", W / 2, H * 0.45);
        }
    }

    /** 画一棵柔和手绘风格的树 */
    private void drawTree(GraphicsContext g, double x, double groundY, double size, double hue) {
        double treeHeight = 80 * size + 20;
        double trunkHeight = treeHeight * 0.35;
        double crownRadius = treeHeight * 0.45;

        double bx = x;
        double by = groundY + Math.sin(x * 0.02) * 4; // 跟随地面起伏

        // === 树干（柔和棕色） ===
        g.setStroke(Color.rgb(120 + (int)(hue * 0.2), 80, 50, 0.5));
        g.setLineWidth(3 * size + 1);
        g.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        g.beginPath();
        g.moveTo(bx, by);
        double trunkTop = by - trunkHeight;
        // 微微弯曲的树干
        double curve = Math.sin(x * 0.5) * 3;
        g.quadraticCurveTo(bx + curve, by - trunkHeight * 0.5, bx + curve * 0.5, trunkTop);
        g.stroke();

        // === 树冠（多层半透明圆，柔和叠色） ===
        double crownCenterY = trunkTop - crownRadius * 0.3;
        int layers = 3;

        Color[] greens = {
                Color.rgb(34, 197 + (int)(hue * 0.15), 80 + (int)(hue * 0.2), 0.35),
                Color.rgb(74, 222 + (int)(hue * 0.1), 100 + (int)(hue * 0.15), 0.30),
                Color.rgb(134, 239 + (int)(hue * 0.05), 120, 0.25)
        };

        for (int li = 0; li < layers; li++) {
            double layerRadius = crownRadius * (0.7 + li * 0.15);
            double lx = bx + (li - 1) * crownRadius * 0.25;
            double ly = crownCenterY + (li - 1) * crownRadius * 0.15;

            // 轻微摆动
            double sway = Math.sin(time * 0.5 + x * 0.1 + li) * 2;
            lx += sway;

            g.setFill(greens[li]);
            g.fillOval(lx - layerRadius, ly - layerRadius, layerRadius * 2, layerRadius * 2);

            // 树冠边缘光晕
            RadialGradient crownGlow = new RadialGradient(0, 0, 0.5, 0.5, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.rgb(255, 255, 255, 0.0)),
                    new Stop(0.7, Color.rgb(255, 255, 255, 0.0)),
                    new Stop(1, Color.rgb(255, 255, 255, 0.04)));
            g.setFill(crownGlow);
            g.fillOval(lx - layerRadius, ly - layerRadius, layerRadius * 2, layerRadius * 2);
        }

        // === 小果实/光点（树冠上的彩色小点） ===
        if (size > 0.5) {
            Random fruitRnd = new Random((long)(x * 100));
            int fruitCount = (int)(size * 6) + 2;
            for (int fi = 0; fi < fruitCount; fi++) {
                double fx = bx + (fruitRnd.nextDouble() - 0.5) * crownRadius * 1.2;
                double fy = crownCenterY + (fruitRnd.nextDouble() - 0.5) * crownRadius * 0.8;
                double fr = 1.5 + fruitRnd.nextDouble() * 2.5;
                Color fc;
                switch (fruitRnd.nextInt(3)) {
                    case 0: fc = Color.rgb(251, 191, 36, 0.5); break;  // 金色
                    case 1: fc = Color.rgb(248, 113, 113, 0.4); break; // 红色
                    default: fc = Color.rgb(167, 139, 250, 0.4); break; // 紫色
                }
                g.setFill(fc);
                g.fillOval(fx - fr, fy - fr, fr * 2, fr * 2);
            }
        }
    }
}
