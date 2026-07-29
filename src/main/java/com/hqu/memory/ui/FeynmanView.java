package com.hqu.memory.ui;

import com.hqu.memory.model.FlashCard;
import com.hqu.memory.service.FeynmanService;
import com.hqu.memory.storage.DataStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;
import java.util.prefs.Preferences;

/**
 * 费曼学习法 AI 对话页面
 * 选卡片 → 向 AI 讲解 → AI追问 → 总结
 */
public class FeynmanView {

    private final BorderPane view;
    private final ComboBox<String> cardSelector;
    private final TextArea chatArea;
    private final TextField inputField;
    private final Button sendBtn;
    private final Button startBtn;
    private final VBox centerBox;

    private FeynmanService feynman;
    private FlashCard currentCard;
    private boolean sessionActive = false;

    private static final Preferences PREFS = Preferences.userNodeForPackage(FeynmanView.class);

    public FeynmanView() {
        view = new BorderPane();
        view.getStyleClass().add("glass-panel");

        // 标题
        Label title = new Label("🤔 费曼学习法");
        title.getStyleClass().add("page-title");

        Label subTitle = new Label("选一张卡片，向 AI 讲解它，直到真懂为止");
        subTitle.setStyle("-fx-text-fill: rgba(255,255,255,0.4); -fx-font-size: 12px; -fx-padding: 0 0 0 12;");

        HBox header = new HBox(0);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 12, 0));
        header.getChildren().addAll(title, subTitle);

        // 卡片选择 + API Key
        cardSelector = new ComboBox<>();
        cardSelector.setPromptText("选择要讲解的卡片");
        cardSelector.setPrefWidth(350);
        cardSelector.getStyleClass().add("glass-combo");

        startBtn = new Button("开始讲解");
        startBtn.setStyle("-fx-background-color: #6366f1; -fx-background-radius: 10; -fx-text-fill: white; "
                + "-fx-font-size: 13px; -fx-padding: 8 20; -fx-cursor: hand; -fx-font-weight: bold;");

        HBox selectRow = new HBox(12);
        selectRow.setAlignment(Pos.CENTER_LEFT);
        selectRow.setPadding(new Insets(0, 0, 12, 0));
        selectRow.getChildren().addAll(cardSelector, startBtn);

        // 对话区域
        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setPrefRowCount(14);
        chatArea.setStyle("-fx-background-color: rgba(255,255,255,0.03); -fx-text-fill: rgba(255,255,255,0.8); "
                + "-fx-font-size: 14px; -fx-border-color: rgba(255,255,255,0.06); -fx-border-radius: 12; "
                + "-fx-control-inner-background: transparent;");

        // 输入区
        inputField = new TextField();
        inputField.setPromptText("输入你的讲解，按 Enter 发送...");
        inputField.setDisable(true);
        inputField.setStyle("-fx-background-color: rgba(255,255,255,0.06); -fx-background-radius: 10; "
                + "-fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 10 14; -fx-border-color: rgba(255,255,255,0.1); -fx-border-radius: 10;");

        sendBtn = new Button("发送");
        sendBtn.setStyle("-fx-background-color: #22c55e; -fx-background-radius: 10; -fx-text-fill: white; "
                + "-fx-font-size: 13px; -fx-padding: 10 20; -fx-cursor: hand; -fx-font-weight: bold;");
        sendBtn.setDisable(true);

        HBox inputRow = new HBox(12);
        inputRow.setPadding(new Insets(12, 0, 0, 0));
        inputRow.getChildren().addAll(inputField, sendBtn);
        HBox.setHgrow(inputField, Priority.ALWAYS);

        centerBox = new VBox(8);
        centerBox.getChildren().addAll(selectRow, chatArea, inputRow);

        view.setTop(header);
        view.setCenter(centerBox);

        // 事件
        startBtn.setOnAction(e -> startSession());
        sendBtn.setOnAction(e -> sendMessage());
        inputField.setOnAction(e -> sendMessage());
    }

    public BorderPane getView() { return view; }

    public void refresh() {
        List<FlashCard> cards = DataStore.loadCards();
        cardSelector.getItems().clear();
        for (FlashCard c : cards) {
            cardSelector.getItems().add(c.getFront().substring(0, Math.min(c.getFront().length(), 40)) + "  [" + c.getCategory() + "]");
        }
        if (!cards.isEmpty()) cardSelector.setValue(null);
    }

    private void startSession() {
        int idx = cardSelector.getSelectionModel().getSelectedIndex();
        if (idx < 0) { showAlert("请先选择一张卡片"); return; }

        String apiKey = PREFS.get("deepseek_api_key", "");
        if (apiKey.isEmpty()) { showAlert("请先在\"识文件\"功能中配置 API Key"); return; }

        List<FlashCard> cards = DataStore.loadCards();
        if (idx >= cards.size()) return;
        currentCard = cards.get(idx);

        try {
            feynman = new FeynmanService(apiKey, currentCard.getFront(), currentCard.getBack());
        } catch (Exception e) {
            showAlert("初始化失败: " + e.getMessage());
            return;
        }

        chatArea.clear();
        chatArea.appendText("🤖 AI 学生：请向我解释「" + currentCard.getFront() + "」这个概念。\n");
        chatArea.appendText("📌 提示：用自己的话讲清楚，我随时会追问你哦！\n\n");

        inputField.setDisable(false);
        sendBtn.setDisable(false);
        startBtn.setDisable(true);
        cardSelector.setDisable(true);
        inputField.requestFocus();
        sessionActive = true;
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || !sessionActive) return;
        inputField.clear();

        chatArea.appendText("🧑 你：" + text + "\n");
        chatArea.setScrollTop(Double.MAX_VALUE);

        sendBtn.setDisable(true);
        inputField.setDisable(true);

        new Thread(() -> {
            try {
                String reply = feynman.chat(text);

                javafx.application.Platform.runLater(() -> {
                    if (reply.startsWith("【理解】")) {
                        chatArea.appendText("🎉 " + reply + "\n\n");
                        chatArea.appendText("✅ 你已经讲明白了这个概念！继续加油！\n");
                        endSession();
                    } else {
                        chatArea.appendText("🤖 " + reply + "\n");
                        chatArea.setScrollTop(Double.MAX_VALUE);
                        sendBtn.setDisable(false);
                        inputField.setDisable(false);
                        inputField.requestFocus();
                    }
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    chatArea.appendText("❌ 出错了: " + e.getMessage() + "\n");
                    sendBtn.setDisable(false);
                    inputField.setDisable(false);
                });
            }
        }).start();
    }

    private void endSession() {
        sessionActive = false;
        startBtn.setDisable(false);
        cardSelector.setDisable(false);
        sendBtn.setDisable(true);
        inputField.setDisable(true);
    }

    private void showAlert(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.getDialogPane().setStyle("-fx-background-color: #1e1b34; -fx-text-fill: white;");
        a.show();
    }
}
