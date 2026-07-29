package com.hqu.memory;

import com.hqu.memory.ui.*;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/**
 * 主窗口：侧边栏导航 + 内容区切换
 */
public class MemoryApp extends Application {

    private final StackPane contentArea = new StackPane();
    private HomeView homeView;
    private CardListView cardListView;
    private ReviewView reviewView;
    private CurveView curveView;
    private StatsView statsView;
    private PomodoroView pomodoroView;
    private ForestView forestView;
    private FeynmanView feynmanView;
    private GraphView graphView;
    private Button homeBtn, cardBtn, reviewBtn, pomoBtn, forestBtn, graphBtn, feynmanBtn, curveBtn, statsBtn;

    @Override
    public void start(Stage stage) {
        homeView = new HomeView();
        cardListView = new CardListView();
        reviewView = new ReviewView();
        curveView = new CurveView();
        statsView = new StatsView();
        pomodoroView = new PomodoroView();
        forestView = new ForestView();
        feynmanView = new FeynmanView();
        graphView = new GraphView();

        homeView.setOnStartReview(() -> {
            reviewView.startReview();
            showView(reviewView.getView());
            setActiveNav(reviewBtn);
        });

        contentArea.getChildren().addAll(
                homeView.getView(),
                cardListView.getView(),
                reviewView.getView(),
                pomodoroView.getView(),
                forestView.getView(),
                feynmanView.getView(),
                graphView.getView(),
                curveView.getView(),
                statsView.getView()
        );
        contentArea.setPadding(new Insets(24));

        VBox sidebar = createSidebar();
        sidebar.setPrefWidth(180);

        BorderPane root = new BorderPane();
        root.setLeft(sidebar);
        root.setCenter(contentArea);

        showView(homeView.getView());
        setActiveNav(homeBtn);

        Scene scene = new Scene(root, 1000, 680);
        scene.getStylesheets().add(getClass().getResource("/styles/glass.css").toExternalForm());

        stage.setTitle("考研记忆助手 - 艾宾浩斯遗忘曲线");
        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(560);
        stage.show();
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(180);

        ScrollPane scrollPane = new ScrollPane(sidebar);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        Label title = new Label("记忆助手");
        title.getStyleClass().add("sidebar-title");

        homeBtn = createNavBtn("🏠  首页");
        cardBtn = createNavBtn("📝  卡片管理");
        reviewBtn = createNavBtn("🔄  开始复习");

        Label focusTitle = new Label("专注与成长");
        focusTitle.setStyle("-fx-text-fill: rgba(255,255,255,0.3); -fx-font-size: 11px; -fx-padding: 16 0 4 10;");
        pomoBtn = createNavBtn("🍅  番茄钟");
        forestBtn = createNavBtn("🌳  记忆森林");

        Label learnTitle = new Label("深度学习");
        learnTitle.setStyle("-fx-text-fill: rgba(255,255,255,0.3); -fx-font-size: 11px; -fx-padding: 16 0 4 10;");
        feynmanBtn = createNavBtn("🤔  费曼学习");
        graphBtn = createNavBtn("🔗  知识图谱");

        Label chartTitle = new Label("数据分析");
        chartTitle.setStyle("-fx-text-fill: rgba(255,255,255,0.3); -fx-font-size: 11px; -fx-padding: 16 0 4 10;");
        curveBtn = createNavBtn("📈  遗忘曲线");
        statsBtn = createNavBtn("📊  学习统计");

        Label importTitle = new Label("导入工具");
        importTitle.setStyle("-fx-text-fill: rgba(255,255,255,0.3); -fx-font-size: 11px; -fx-padding: 16 0 4 10;");

        Button fileImportBtn = createNavBtn("📄  识文件");
        fileImportBtn.setStyle("-fx-background-color: rgba(16,185,129,0.15); -fx-background-radius: 10; -fx-text-fill: #34d399; -fx-font-size: 13px; -fx-padding: 10 18; -fx-cursor: hand; -fx-border-color: rgba(16,185,129,0.2); -fx-border-radius: 10;");
        fileImportBtn.setOnAction(e -> {
            cardListView.refresh();
            cardListView.handleScanFile();
            showView(cardListView.getView());
            setActiveNav(cardBtn);
        });

        Button imgImportBtn = createNavBtn("🖼  识图片");
        imgImportBtn.setStyle("-fx-background-color: rgba(139,92,246,0.15); -fx-background-radius: 10; -fx-text-fill: #a78bfa; -fx-font-size: 13px; -fx-padding: 10 18; -fx-cursor: hand; -fx-border-color: rgba(139,92,246,0.2); -fx-border-radius: 10;");
        imgImportBtn.setOnAction(e -> {
            cardListView.refresh();
            cardListView.handleScanImage();
            showView(cardListView.getView());
            setActiveNav(cardBtn);
        });

        homeBtn.setOnAction(e -> { homeView.refresh(); showView(homeView.getView()); setActiveNav(homeBtn); });
        cardBtn.setOnAction(e -> { cardListView.refresh(); showView(cardListView.getView()); setActiveNav(cardBtn); });
        reviewBtn.setOnAction(e -> { reviewView.startReview(); showView(reviewView.getView()); setActiveNav(reviewBtn); });
        pomoBtn.setOnAction(e -> { pomodoroView.refresh(); showView(pomodoroView.getView()); setActiveNav(pomoBtn); });
        forestBtn.setOnAction(e -> { forestView.refresh(); showView(forestView.getView()); setActiveNav(forestBtn); });
        feynmanBtn.setOnAction(e -> { feynmanView.refresh(); showView(feynmanView.getView()); setActiveNav(feynmanBtn); });
        graphBtn.setOnAction(e -> { graphView.refresh(); showView(graphView.getView()); setActiveNav(graphBtn); });
        curveBtn.setOnAction(e -> { curveView.refresh(); showView(curveView.getView()); setActiveNav(curveBtn); });
        statsBtn.setOnAction(e -> { statsView.refresh(); showView(statsView.getView()); setActiveNav(statsBtn); });

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebar.getChildren().addAll(
                title,
                homeBtn, cardBtn, reviewBtn,
                focusTitle, pomoBtn, forestBtn,
                learnTitle, feynmanBtn, graphBtn,
                chartTitle, curveBtn, statsBtn,
                importTitle, fileImportBtn, imgImportBtn,
                spacer
        );

        root.setLeft(scrollPane);
        return sidebar;
    }

    private Button createNavBtn(String text) {
        Button btn = new Button(text);
        btn.getStyleClass().add("nav-btn");
        btn.setMaxWidth(Double.MAX_VALUE);
        return btn;
    }

    private void showView(Node view) {
        for (Node child : contentArea.getChildren()) child.setVisible(false);
        view.setVisible(true);
        view.toFront();
    }

    private void setActiveNav(Button active) {
        for (Button btn : new Button[]{homeBtn, cardBtn, reviewBtn, pomoBtn, forestBtn, feynmanBtn, graphBtn, curveBtn, statsBtn})
            btn.getStyleClass().remove("active");
        active.getStyleClass().add("active");
    }

    public static void main(String[] args) { launch(args); }
}
