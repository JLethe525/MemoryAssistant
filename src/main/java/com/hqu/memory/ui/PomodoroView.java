package com.hqu.memory.ui;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
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
import javafx.util.Duration;

import java.time.LocalDate;
import java.util.prefs.Preferences;

/**
 * 番茄钟专注页面
 * 25分钟专注 + 5分钟休息，柔和画风圆环倒计时
 */
public class PomodoroView {

    private final BorderPane view;
    private final Canvas canvas;
    private final Label statusLabel;
    private final Button startBtn;
    private final Label statsLabel;

    private Timeline timer;
    private int totalSeconds;
    private int remainingSeconds;
    private boolean isRunning = false;
    private boolean isWork = true;

    private static final int WORK_MINUTES = 25;
    private static final int REST_MINUTES = 5;
    private static final Preferences PREFS = Preferences.userNodeForPackage(PomodoroView.class);

    private int todayPomodoros = 0;

    public PomodoroView() {
        view = new BorderPane();
        view.getStyleClass().add("glass-panel");

        // 标题
        Label title = new Label("🍅 番茄钟");
        title.getStyleClass().add("page-title");

        statsLabel = new Label();
        statsLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.4); -fx-font-size: 12px;");

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 10, 0));
        header.getChildren().addAll(title, statsLabel);

        // Canvas 圆环
        canvas = new Canvas(320, 320);
        canvas.setEffect(new DropShadow(25, Color.rgb(251, 146, 60, 0.2)));

        StackPane canvasBox = new StackPane(canvas);
        canvasBox.setAlignment(Pos.CENTER);
        canvasBox.setPadding(new Insets(10));

        // 状态文字
        statusLabel = new Label("专注 25:00");
        statusLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.8); -fx-font-size: 18px; -fx-font-weight: bold;");
        statusLabel.setAlignment(Pos.CENTER);

        // 按钮
        startBtn = new Button("开始专注");
        startBtn.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, #f97316, #fb923c); "
                + "-fx-background-radius: 30; -fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold; "
                + "-fx-padding: 14 48; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(249,115,22,0.4), 15, 0, 0, 4);");

        Button clearBtn = new Button("重置");
        clearBtn.setStyle("-fx-background-color: rgba(255,255,255,0.08); -fx-background-radius: 30; -fx-text-fill: rgba(255,255,255,0.7); "
                + "-fx-font-size: 13px; -fx-padding: 10 24; -fx-cursor: hand; -fx-border-color: rgba(255,255,255,0.15); -fx-border-radius: 30;");

        // 聚集布局
        VBox centerBox = new VBox(12);
        centerBox.setAlignment(Pos.CENTER);
        HBox btnRow = new HBox(14);
        btnRow.setAlignment(Pos.CENTER);
        btnRow.getChildren().addAll(startBtn, clearBtn);
        centerBox.getChildren().addAll(canvasBox, statusLabel, btnRow);

        // 底部提示
        Label tip = new Label("每完成一个番茄钟，记忆森林就会长出一棵新树");
        tip.setStyle("-fx-text-fill: rgba(255,255,255,0.25); -fx-font-size: 12px;");
        HBox tipBox = new HBox(tip);
        tipBox.setAlignment(Pos.CENTER);
        tipBox.setPadding(new Insets(16, 0, 0, 0));

        view.setTop(header);
        view.setCenter(centerBox);
        view.setBottom(tipBox);

        // 加载今日数据
        loadTodayStats();
        resetTimer();

        // 事件
        startBtn.setOnAction(e -> toggleTimer());
        clearBtn.setOnAction(e -> resetAll());

        // 初始绘制
        draw();
    }

    public BorderPane getView() { return view; }

    public int getTodayPomodoros() { return todayPomodoros; }

    /** 刷新统计（外部调用，比如记忆森林切换时） */
    public void refresh() {
        loadTodayStats();
        draw();
    }

    // ---- 私有方法 ----

    private void loadTodayStats() {
        String today = LocalDate.now().toString();
        todayPomodoros = PREFS.getInt("pomo_" + today, 0);
        statsLabel.setText("今日完成 " + todayPomodoros + " 个");
    }

    private void saveTodayStats() {
        String today = LocalDate.now().toString();
        PREFS.putInt("pomo_" + today, todayPomodoros);
        statsLabel.setText("今日完成 " + todayPomodoros + " 个");
    }

    private void resetTimer() {
        isWork = true;
        totalSeconds = WORK_MINUTES * 60;
        remainingSeconds = totalSeconds;
        statusLabel.setText("专注 25:00");
        startBtn.setText("开始专注");
        draw();
    }

    /** 重置按钮：停止计时并恢复到初始状态 */
    private void resetAll() {
        if (timer != null) timer.stop();
        isRunning = false;
        isWork = true;
        totalSeconds = WORK_MINUTES * 60;
        remainingSeconds = totalSeconds;
        statusLabel.setText("专注 25:00");
        startBtn.setText("开始专注");
        draw();
    }

    private void toggleTimer() {
        if (isRunning) {
            // 暂停
            if (timer != null) timer.pause();
            isRunning = false;
            startBtn.setText("继续");
        } else {
            if (remainingSeconds <= 0) {
                // 重置后开始
                resetTimer();
            }
            // 启动
            if (timer == null || timer.getStatus() == Animation.Status.STOPPED) {
                timer = new Timeline();
                timer.setCycleCount(Timeline.INDEFINITE);
                KeyFrame kf = new KeyFrame(Duration.seconds(1), e -> tick());
                timer.getKeyFrames().add(kf);
            }
            timer.play();
            isRunning = true;
            startBtn.setText("暂停");
        }
    }

    private void tick() {
        if (remainingSeconds > 0) {
            remainingSeconds--;
            updateDisplay();
            draw();
        } else {
            // 时间到
            timer.stop();
            isRunning = false;
            if (isWork) {
                todayPomodoros++;
                saveTodayStats();
                statusLabel.setText("🎉 专注完成！休息一下吧");
                startBtn.setText("开始休息");
                isWork = false;
                totalSeconds = REST_MINUTES * 60;
                remainingSeconds = totalSeconds;
            } else {
                statusLabel.setText("☕ 休息结束，继续加油");
                startBtn.setText("开始专注");
                isWork = true;
                totalSeconds = WORK_MINUTES * 60;
                remainingSeconds = totalSeconds;
            }
            // 平滑过渡动画
            animateTransition();
            draw();
        }
    }

    private void updateDisplay() {
        int min = remainingSeconds / 60;
        int sec = remainingSeconds % 60;
        String phase = isWork ? "专注" : "休息";
        statusLabel.setText(phase + " " + String.format("%02d:%02d", min, sec));
    }

    private void animateTransition() {
        Timeline anim = new Timeline();
        anim.getKeyFrames().add(new KeyFrame(Duration.millis(600),
                new KeyValue(canvas.opacityProperty(), 0.3, Interpolator.EASE_BOTH),
                new KeyValue(canvas.opacityProperty(), 1.0, Interpolator.EASE_BOTH)));
        anim.play();
    }

    private void draw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double W = canvas.getWidth();
        double H = canvas.getHeight();
        if (W < 50) return;

        g.clearRect(0, 0, W, H);

        double cx = W / 2, cy = H / 2;
        double radius = Math.min(W, H) / 2 - 30;
        double progress = totalSeconds > 0 ? (double) remainingSeconds / totalSeconds : 0;

        Color bgColor = isWork ? Color.rgb(255, 255, 255, 0.04) : Color.rgb(255, 255, 255, 0.04);
        Color trackColor = isWork ? Color.rgb(255, 255, 255, 0.08) : Color.rgb(255, 255, 255, 0.08);
        Color arcColor = isWork ? Color.rgb(251, 146, 60, 0.85) : Color.rgb(52, 211, 153, 0.85);
        Color glowColor = isWork ? Color.rgb(251, 146, 60, 0.15) : Color.rgb(52, 211, 153, 0.15);

        // 外圈光晕
        g.setFill(glowColor);
        g.fillOval(cx - radius - 12, cy - radius - 12, (radius + 12) * 2, (radius + 12) * 2);

        // 环形轨道
        g.setStroke(trackColor);
        g.setLineWidth(8);
        g.strokeOval(cx - radius, cy - radius, radius * 2, radius * 2);

        // 进度弧
        if (progress < 1) {
            double angle = 360 * (1 - progress);
            g.setStroke(arcColor);
            g.setLineWidth(8);
            g.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
            g.strokeArc(cx - radius, cy - radius, radius * 2, radius * 2, 90, -angle, javafx.scene.shape.ArcType.OPEN);
        }

        // 中心时间
        int min = remainingSeconds / 60;
        int sec = remainingSeconds % 60;
        String timeStr = String.format("%02d:%02d", min, sec);

        g.setFill(Color.rgb(255, 255, 255, 0.9));
        g.setFont(Font.font("Microsoft YaHei", FontWeight.LIGHT, 48));
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText(timeStr, cx, cy + 16);

        // 小标签
        g.setFill(Color.rgb(255, 255, 255, 0.35));
        g.setFont(Font.font("Microsoft YaHei", 13));
        g.fillText(isWork ? "专注中" : "休息中", cx, cy + 46);
    }
}
