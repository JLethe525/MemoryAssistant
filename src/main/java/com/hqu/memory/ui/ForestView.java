package com.hqu.memory.ui;

import com.hqu.memory.model.FlashCard;
import com.hqu.memory.storage.DataStore;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
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
 * 点击树显示数据，悬浮高亮，干净手绘风
 */
public class ForestView {

    private final BorderPane view;
    private final Canvas canvas;
    private final Label titleLabel;
    private final Label statsLabel;
    private final Label clickHint;
    private double time = 0;

    private static final Preferences PREFS = Preferences.userNodeForPackage(ForestView.class);

    // 树数据数组（用于点击检测）
    private double[] treeX, treeY, treeSize;
    private String[] treeLabel;
    private int treeCount = 0;
    private int hoveredIdx = -1;
    private int clickedIdx = -1;
    private Tooltip activeTip = null;

    public ForestView() {
        view = new BorderPane();
        view.getStyleClass().add("glass-panel");

        titleLabel = new Label("🌳 记忆森林");
        titleLabel.getStyleClass().add("page-title");

        statsLabel = new Label();
        statsLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.4); -fx-font-size: 12px; -fx-padding: 0 0 0 12;");

        HBox header = new HBox(0);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 10, 0));
        header.getChildren().addAll(titleLabel, statsLabel);

        canvas = new Canvas(860, 440);
        canvas.setEffect(new DropShadow(20, Color.rgb(34, 197, 94, 0.1)));

        StackPane canvasBox = new StackPane(canvas);
        canvasBox.setPadding(new Insets(0));
        VBox.setVgrow(canvasBox, Priority.ALWAYS);

        // 底部提示
        clickHint = new Label("💡 点击树木查看数据  ·  复习卡片为树木浇水  ·  番茄钟长出新树");
        clickHint.setStyle("-fx-text-fill: rgba(255,255,255,0.25); -fx-font-size: 12px;");
        HBox hintBox = new HBox(clickHint);
        hintBox.setAlignment(Pos.CENTER);
        hintBox.setPadding(new Insets(12, 0, 0, 0));

        view.setTop(header);
        view.setCenter(canvasBox);
        view.setBottom(hintBox);

        // 交互：鼠标移动检测悬停
        canvas.addEventHandler(MouseEvent.MOUSE_MOVED, e -> {
            int old = hoveredIdx;
            hoveredIdx = findTreeAt(e.getX(), e.getY());
            if (old != hoveredIdx) draw();
            updateCursor();
        });

        // 点击弹提示（点击同一棵树切换显示/隐藏）
        canvas.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            int idx = findTreeAt(e.getX(), e.getY());
            if (idx >= 0 && idx < treeCount) {
                if (clickedIdx == idx) {
                    // 再次点击同一棵树 -> 隐藏
                    clickedIdx = -1;
                    Tooltip.uninstall(canvas, activeTip);
                    activeTip = null;
                } else {
                    clickedIdx = idx;
                    if (activeTip != null) Tooltip.uninstall(canvas, activeTip);
                    Tooltip tip = new Tooltip(treeLabel[idx]);
                    tip.setStyle("-fx-background-color: #1e1b34; -fx-text-fill: white; -fx-font-size: 12px; "
                            + "-fx-background-radius: 10; -fx-padding: 8 14; -fx-border-color: rgba(255,255,255,0.1); -fx-border-radius: 10;");
                    Tooltip.install(canvas, tip);
                    tip.show(canvas, e.getScreenX(), e.getScreenY() - 30);
                    activeTip = tip;
                }
            } else {
                // 点击空白区域 -> 隐藏
                clickedIdx = -1;
                if (activeTip != null) {
                    Tooltip.uninstall(canvas, activeTip);
                    activeTip = null;
                }
            }
        });

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

    private void updateCursor() {
        Canvas c = canvas;
        if (hoveredIdx >= 0) {
            c.setStyle("-fx-cursor: hand;");
        } else {
            c.setStyle("-fx-cursor: default;");
        }
    }

    private int findTreeAt(double mx, double my) {
        for (int i = 0; i < treeCount; i++) {
            double dx = mx - treeX[i];
            double dy = my - treeY[i];
            // 点击范围：树冠半径附近
            double hitRadius = 10 + treeSize[i] * 20;
            if (dx * dx + dy * dy < hitRadius * hitRadius) return i;
        }
        return -1;
    }

    private void draw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double W = canvas.getWidth();
        double H = canvas.getHeight();
        if (W < 100 || H < 100) return;

        g.clearRect(0, 0, W, H);

        // 天空
        LinearGradient sky = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(15, 15, 40, 0.5)),
                new Stop(1, Color.rgb(20, 25, 50, 0.2)));
        g.setFill(sky);
        g.fillRect(0, 0, W, H * 0.65);

        // 地面
        LinearGradient ground = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(22, 40, 28, 0.4)),
                new Stop(1, Color.rgb(15, 30, 20, 0.2)));
        g.setFill(ground);
        g.fillRect(0, H * 0.65, W, H * 0.35);

        double groundY = H * 0.65;
        g.setStroke(Color.rgb(34, 197, 94, 0.12));
        g.setLineWidth(1.5);
        g.beginPath();
        for (int px = 0; px <= W; px++) {
            double wave = Math.sin(px * 0.01 + time * 0.2) * 3;
            if (px == 0) g.moveTo(px, groundY + wave);
            else g.lineTo(px, groundY + wave);
        }
        g.stroke();

        // 星星（更淡）
        Random rnd = new Random(42);
        for (int i = 0; i < 25; i++) {
            double sx = rnd.nextDouble() * W;
            double sy = rnd.nextDouble() * H * 0.5;
            double twinkle = 0.1 + 0.1 * Math.sin(time * 1.5 + i * 2.3);
            g.setFill(Color.rgb(255, 255, 255, twinkle));
            g.fillOval(sx, sy, 1.2, 1.2);
        }

        // 数据
        List<FlashCard> cards = DataStore.loadCards();
        int pomos = PREFS.getInt("pomo_" + LocalDate.now().toString(), 0);

        int[] stageCounts = new int[6];
        for (FlashCard c : cards) stageCounts[c.getStage()]++;

        treeCount = Math.max(pomos, 1) + stageCounts[5] / 3 + (int)(cards.size() / 10);
        if (treeCount > 30) treeCount = 30;

        treeX = new double[treeCount];
        treeY = new double[treeCount];
        treeSize = new double[treeCount];
        treeLabel = new String[treeCount];

        Random treeRnd = new Random(123);

        for (int i = 0; i < treeCount; i++) {
            treeX[i] = 40 + treeRnd.nextDouble() * (W - 80);
            double base = 0.35;
            double fromPomo = Math.min(pomos, 10) / 10.0 * 0.35;
            double fromCards = Math.min(cards.size(), 50) / 50.0 * 0.3;
            treeSize[i] = base + fromPomo + fromCards + treeRnd.nextDouble() * 0.1;
            if (treeSize[i] > 1.0) treeSize[i] = 1.0;
            // 生成树种不同的标签
            double gy = groundY + Math.sin(treeX[i] * 0.02) * 4;
            double th = (80 * treeSize[i] + 20) * 0.65;
            treeY[i] = gy - th;
            treeLabel[i] = "🌲 " + (i < pomos ? "番茄树 #" + (i+1) : "记忆树 #" + (i+1))
                    + "\n大小: " + (int)(treeSize[i] * 100) + "%";
        }

        // 从右到左画
        for (int i = treeCount - 1; i >= 0; i--) {
            boolean hovered = (i == hoveredIdx);
            drawTree(g, treeX[i], groundY, treeSize[i], i, hovered);
        }

        if (cards.isEmpty() && pomos == 0) {
            g.setFill(Color.rgb(255, 255, 255, 0.2));
            g.setFont(Font.font("Microsoft YaHei", 14));
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText("🌱 开始复习和完成番茄钟，你的森林会在这里生长", W / 2, H * 0.45);
        }
    }

    /** 干净简洁的树 */
    private void drawTree(GraphicsContext g, double x, double groundY, double size, int idx, boolean hovered) {
        double treeHeight = 70 * size + 20;
        double trunkH = treeHeight * 0.4;
        double crownR = treeHeight * 0.4;

        double bx = x;
        double by = groundY + Math.sin(x * 0.02) * 4;

        double trunkTop = by - trunkH;
        double crownY = trunkTop - crownR * 0.2;

        // 树干
        double trunkW = 3 * size + 1;
        g.setStroke(Color.rgb(100, 70, 40, 0.5));
        g.setLineWidth(trunkW);
        g.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        g.strokeLine(bx, by, bx, trunkTop);

        // 树冠（2层，半透明叠翠）
        Color baseGreen = Color.rgb(34, 197, 94, 0.55);
        Color lightGreen = Color.rgb(74, 222, 128, 0.40);

        double sway = Math.sin(time * 0.5 + idx) * 1.5;
        double cx = bx + sway;
        double cy = crownY;

        if (hovered) {
            // 悬停高亮：加发光圈
            g.setFill(Color.rgb(255, 255, 200, 0.08));
            g.fillOval(cx - crownR - 8, cy - crownR - 8, (crownR + 8) * 2, (crownR + 8) * 2);
        }

        // 底层（大）
        g.setFill(baseGreen);
        g.fillOval(cx - crownR, cy - crownR, crownR * 2, crownR * 2);
        // 上层（小偏上）
        double topR = crownR * 0.7;
        double topY = cy - crownR * 0.25;
        g.setFill(lightGreen);
        g.fillOval(cx - topR, topY - topR, topR * 2, topR * 2);

        // 树冠描边（干净轮廓）
        g.setStroke(Color.rgb(34, 197, 94, 0.25));
        g.setLineWidth(1);
        g.strokeOval(cx - crownR, cy - crownR, crownR * 2, crownR * 2);

        // 少量小果（减少到最多3颗，颜色收敛）
        if (size > 0.5) {
            Random fr = new Random((long)(x * 100));
            int n = Math.min((int)(size * 3), 3);
            for (int fi = 0; fi < n; fi++) {
                double fx = cx + (fr.nextDouble() - 0.5) * crownR * 1.0;
                double fy = cy + (fr.nextDouble() - 0.5) * crownR * 0.7;
                g.setFill(Color.rgb(251, 191, 36, 0.5));
                g.fillOval(fx - 2, fy - 2, 4, 4);
            }
        }
    }
}
