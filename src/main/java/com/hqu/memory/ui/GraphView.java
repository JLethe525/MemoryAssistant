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
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识图谱
 * 卡片作为节点，同分类连线，点击显示详情
 */
public class GraphView {

    private final BorderPane view;
    private final Canvas canvas;
    private final Label titleLabel;
    private final Label infoLabel;
    private double time = 0;

    // 节点数据
    private List<NodeData> nodes = new ArrayList<>();
    private int hoveredNode = -1;
    private int selectedNode = -1;

    private static class NodeData {
        double x, y, radius;
        String label, category;
        int stage;
        String front, back;
        int catIndex;
    }

    private double scale = 1.0;
    private double offsetX = 0, offsetY = 0;
    private double lastMouseX, lastMouseY;
    private boolean dragging = false;

    private static final String[] CAT_COLORS = {
            "#3b82f6", "#ef4444", "#22c55e", "#f59e0b",
            "#a855f7", "#ec4899", "#06b6d4", "#f97316"
    };

    public GraphView() {
        view = new BorderPane();
        view.getStyleClass().add("glass-panel");

        titleLabel = new Label("🔗 知识图谱");
        titleLabel.getStyleClass().add("page-title");

        infoLabel = new Label("💡 悬停查看卡片，点击选中");
        infoLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.3); -fx-font-size: 12px; -fx-padding: 0 0 0 12;");

        HBox header = new HBox(0);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 10, 0));
        header.getChildren().addAll(titleLabel, infoLabel);

        canvas = new Canvas(860, 440);
        canvas.setEffect(new DropShadow(20, Color.rgb(99, 102, 241, 0.1)));

        StackPane canvasBox = new StackPane(canvas);
        canvasBox.setPadding(new Insets(0));
        VBox.setVgrow(canvasBox, Priority.ALWAYS);

        // 底部选中卡片详情
        Label detailLabel = new Label();
        detailLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.6); -fx-font-size: 13px;");
        detailLabel.setWrapText(true);
        detailLabel.setPadding(new Insets(8, 0, 0, 0));
        VBox bottomBox = new VBox(detailLabel);

        view.setTop(header);
        view.setCenter(canvasBox);
        view.setBottom(bottomBox);

        // 交互
        canvas.addEventHandler(MouseEvent.MOUSE_MOVED, e -> {
            int old = hoveredNode;
            double mx = (e.getX() - offsetX) / scale;
            double my = (e.getY() - offsetY) / scale;
            hoveredNode = findNode(mx, my);
            if (old != hoveredNode) draw();
            canvas.setStyle(hoveredNode >= 0 ? "-fx-cursor: hand;" : "-fx-cursor: default;");
        });

        canvas.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            double mx = (e.getX() - offsetX) / scale;
            double my = (e.getY() - offsetY) / scale;
            int idx = findNode(mx, my);
            if (idx >= 0) {
                if (selectedNode == idx) {
                    selectedNode = -1;
                    detailLabel.setText("");
                    infoLabel.setText("💡 悬停查看卡片，点击选中");
                    infoLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.3); -fx-font-size: 12px; -fx-padding: 0 0 0 12;");
                } else {
                    selectedNode = idx;
                    NodeData n = nodes.get(idx);
                    detailLabel.setText("📌 " + n.front + "\n📝 " + n.back);
                    infoLabel.setText("已选中 · " + n.category + " · stage " + n.stage);
                    infoLabel.setStyle("-fx-text-fill: rgba(255,200,100,0.6); -fx-font-size: 12px; -fx-padding: 0 0 0 12;");
                }
            } else {
                selectedNode = -1;
                detailLabel.setText("");
                infoLabel.setText("💡 悬停查看卡片，点击选中");
                infoLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.3); -fx-font-size: 12px; -fx-padding: 0 0 0 12;");
            }
            draw();
        });

        // 滚轮缩放
        canvas.setOnScroll(e -> {
            double delta = 1 + e.getDeltaY() * 0.001;
            double newScale = scale * delta;
            if (newScale < 0.3) newScale = 0.3;
            if (newScale > 3.0) newScale = 3.0;
            double mx = e.getX();
            double my = e.getY();
            offsetX = mx - (mx - offsetX) * (newScale / scale);
            offsetY = my - (my - offsetY) * (newScale / scale);
            scale = newScale;
            draw();
        });

        // 鼠标拖拽
        canvas.setOnMousePressed(e -> { lastMouseX = e.getX(); lastMouseY = e.getY(); dragging = true; });
        canvas.setOnMouseDragged(e -> {
            if (!dragging) return;
            offsetX += e.getX() - lastMouseX;
            offsetY += e.getY() - lastMouseY;
            lastMouseX = e.getX();
            lastMouseY = e.getY();
            draw();
        });
        canvas.setOnMouseReleased(e -> dragging = false);

        // 呼吸动画

        // 滚轮缩放
        canvas.setOnScroll(e -> {
            double delta = 1 + e.getDeltaY() * 0.001;
            double newScale = scale * delta;
            if (newScale < 0.3) newScale = 0.3;
            if (newScale > 3.0) newScale = 3.0;
            // 以鼠标位置为中心缩放
            double mx = e.getX();
            double my = e.getY();
            offsetX = mx - (mx - offsetX) * (newScale / scale);
            offsetY = my - (my - offsetY) * (newScale / scale);
            scale = newScale;
            draw();
        });

        // 鼠标拖拽平移
        canvas.setOnMousePressed(e -> { lastMouseX = e.getX(); lastMouseY = e.getY(); dragging = true; });
        canvas.setOnMouseDragged(e -> {
            if (!dragging) return;
            offsetX += e.getX() - lastMouseX;
            offsetY += e.getY() - lastMouseY;
            lastMouseX = e.getX();
            lastMouseY = e.getY();
            draw();
        });
        canvas.setOnMouseReleased(e -> dragging = false);

        // 呼吸动画
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                time += 0.005;
                draw();
            }
        };
        timer.start();
    }

    public BorderPane getView() { return view; }

    public void refresh() {
        buildGraph();
    }

    private void buildGraph() {
        List<FlashCard> cards = DataStore.loadCards();
        if (cards == null || cards.isEmpty()) {
            nodes = new ArrayList<>();
            return;
        }

        Map<String, Integer> catIndexMap = new HashMap<>();
        List<String> cats = cards.stream().map(FlashCard::getCategory).distinct().sorted().collect(Collectors.toList());
        for (int i = 0; i < cats.size(); i++) catIndexMap.put(cats.get(i), i);

        double W = canvas.getWidth();
        double H = canvas.getHeight();
        if (W < 100) W = 860;
        if (H < 100) H = 440;

        // 力导向布局简化版：按分类分组，同分类靠近
        Map<String, List<FlashCard>> grouped = cards.stream().collect(Collectors.groupingBy(FlashCard::getCategory));
        int totalCats = grouped.size();
        List<NodeData> newNodes = new ArrayList<>();
        Random rnd = new Random(42);

        int[] idxInCat = new int[totalCats];
        for (var entry : grouped.entrySet()) {
            String cat = entry.getKey();
            List<FlashCard> catCards = entry.getValue();
            int ci = catIndexMap.get(cat);

            // 该分类的中心位置（均匀分布在圆形上）
            double angle = 2 * Math.PI * ci / totalCats;
            double cx = W / 2 + Math.cos(angle) * (Math.min(W, H) * 0.25);
            double cy = H / 2 + Math.sin(angle) * (Math.min(W, H) * 0.25);

            for (int j = 0; j < catCards.size(); j++) {
                FlashCard c = catCards.get(j);
                NodeData nd = new NodeData();
                // 分类内微偏移
                double spread = Math.min(catCards.size(), 15) * 8;
                nd.x = cx + (rnd.nextDouble() - 0.5) * spread;
                nd.y = cy + (rnd.nextDouble() - 0.5) * spread;
                nd.radius = 12 + c.getStage() * 3;
                nd.label = c.getFront().length() > 10 ? c.getFront().substring(0, 10) + ".." : c.getFront();
                nd.category = cat;
                nd.stage = c.getStage();
                nd.front = c.getFront();
                nd.back = c.getBack();
                nd.catIndex = ci;
                newNodes.add(nd);
            }
        }
        nodes = newNodes;
    }

    private int findNode(double mx, double my) {
        // mx, my 已经在调用处做了坐标变换，所以直接用
        for (int i = 0; i < nodes.size(); i++) {
            NodeData n = nodes.get(i);
            double dx = mx - n.x;
            double dy = my - n.y;
            if (dx * dx + dy * dy < (n.radius + 8) * (n.radius + 8)) return i;
        }
        return -1;
    }

    private void draw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double W = canvas.getWidth();
        double H = canvas.getHeight();
        if (W < 100 || H < 100) return;

        g.clearRect(0, 0, W, H);

        // 节点数据为空
        if (nodes.isEmpty()) {
            g.setFill(Color.rgb(255, 255, 255, 0.2));
            g.setFont(Font.font("Microsoft YaHei", 14));
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText("📚 添加卡片后，知识图谱会自动生成", W / 2, H / 2);
            return;
        }

        // 画连线（同分类节点间连线）
        g.save();
        g.translate(offsetX, offsetY);
        g.scale(scale, scale);
        g.setLineWidth(0.8);
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                if (!nodes.get(i).category.equals(nodes.get(j).category)) continue;
                Color lineColor = Color.web(CAT_COLORS[nodes.get(i).catIndex % CAT_COLORS.length], 0.15);
                g.setStroke(lineColor);
                g.strokeLine(nodes.get(i).x, nodes.get(i).y, nodes.get(j).x, nodes.get(j).y);
            }
        }

        // 画节点
        for (int i = 0; i < nodes.size(); i++) {
            NodeData n = nodes.get(i);
            double nx = n.x;
            double ny = n.y;
            double nr = n.radius;
            boolean hovered = (i == hoveredNode);
            boolean selected = (i == selectedNode);
            Color baseColor = Color.web(CAT_COLORS[n.catIndex % CAT_COLORS.length]);

            double r = nr;
            if (hovered) r += 4;
            if (selected) r += 3;

            RadialGradient glow = new RadialGradient(0, 0, 0.5, 0.5, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.rgb((int)(baseColor.getRed()*255), (int)(baseColor.getGreen()*255), (int)(baseColor.getBlue()*255), 0.3)),
                    new Stop(1, Color.rgb((int)(baseColor.getRed()*255), (int)(baseColor.getGreen()*255), (int)(baseColor.getBlue()*255), 0)));
            g.setFill(glow);
            g.fillOval(nx - r - 6, ny - r - 6, (r + 6) * 2, (r + 6) * 2);

            g.setFill(Color.rgb((int)(baseColor.getRed()*255), (int)(baseColor.getGreen()*255), (int)(baseColor.getBlue()*255), 0.45));
            g.fillOval(nx - r, ny - r, r * 2, r * 2);
            g.setStroke(baseColor);
            g.setLineWidth(selected ? 2.5 : 1.5);
            g.strokeOval(nx - r, ny - r, r * 2, r * 2);

            g.setFill(Color.rgb(255, 255, 255, 0.8));
            g.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, (int)Math.min(r * 0.7, 12)));
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText(String.valueOf(n.stage), nx, ny + 4);

            if (hovered) {
                g.setFill(Color.rgb(255, 255, 255, 0.7));
                g.setFont(Font.font("Microsoft YaHei", 10));
                g.setTextAlign(TextAlignment.CENTER);
                g.fillText(n.label, nx, ny - r - 10);
            }
        }
        g.restore();

        // 分类图例
        Map<String, Integer> catIdxMap = new LinkedHashMap<>();
        for (NodeData n : nodes) {
            if (!catIdxMap.containsKey(n.category)) {
                catIdxMap.put(n.category, n.catIndex);
            }
        }
        int li = 0;
        for (var entry : catIdxMap.entrySet()) {
            Color c = Color.web(CAT_COLORS[entry.getValue() % CAT_COLORS.length]);
            double lx = 15;
            double ly = 15 + li * 22;
            g.setFill(c);
            g.fillRoundRect(lx, ly, 10, 10, 2, 2);
            g.setFill(Color.rgb(255, 255, 255, 0.6));
            g.setFont(Font.font("Microsoft YaHei", 11));
            g.setTextAlign(TextAlignment.LEFT);
            g.fillText(entry.getKey(), lx + 16, ly + 9);
            li++;
        }
    }
}
