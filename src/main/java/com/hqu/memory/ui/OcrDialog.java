package com.hqu.memory.ui;

import com.google.gson.JsonObject;
import com.hqu.memory.model.FlashCard;
import com.hqu.memory.service.OcrService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * 导入文件生成卡片弹窗（支持多卡批量生成）
 */
public class OcrDialog {

    private static final Preferences PREFS = Preferences.userNodeForPackage(OcrDialog.class);

    private static boolean isImage(File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".bmp") || n.endsWith(".gif");
    }

    /** 返回用户勾选确认导入的卡片列表，取消返回 null */
    public static List<FlashCard> show(List<String> categories, boolean imageMode) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(imageMode ? "🖼️ 识别图片生成卡片" : "📄 识别文件生成卡片");

        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #111827;");

        // ---- API Key ----
        Label keyLabel = new Label("API Key（DeepSeek）");
        keyLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 13px;");

        PasswordField keyField = new PasswordField();
        keyField.setPromptText("输入 DeepSeek API Key");
        String savedKey = PREFS.get("deepseek_api_key", "");
        if (!savedKey.isEmpty()) keyField.setText(savedKey);
        keyField.setStyle("-fx-background-color: rgba(255,255,255,0.06); -fx-background-radius: 10; "
                + "-fx-text-fill: white; -fx-font-size: 13px; -fx-padding: 10 14; -fx-border-color: rgba(255,255,255,0.1); -fx-border-radius: 10;");

        // ---- 选择文件 ----
        Label fileTitle = new Label(imageMode ? "选择图片（PNG / JPG / BMP / GIF）" : "选择文件（TXT / DOCX / PDF）");
        fileTitle.setStyle("-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 13px;");

        Button chooseBtn = new Button("选择文件");
        chooseBtn.setStyle("-fx-background-color: #6366f1; -fx-background-radius: 10; -fx-text-fill: white; -fx-padding: 8 18; -fx-cursor: hand;");

        Label fileLabel = new Label("未选择文件");
        fileLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.35); -fx-font-size: 12px;");

        HBox chooseRow = new HBox(12);
        chooseRow.setAlignment(Pos.CENTER_LEFT);
        chooseRow.getChildren().addAll(chooseBtn, fileLabel);

        ImageView preview = new ImageView();
        preview.setFitWidth(300);
        preview.setFitHeight(180);
        preview.setPreserveRatio(true);

        TextArea textPreview = new TextArea();
        textPreview.setWrapText(true);
        textPreview.setEditable(false);
        textPreview.setPrefRowCount(4);
        textPreview.setStyle("-fx-background-color: rgba(255,255,255,0.04); -fx-text-fill: rgba(255,255,255,0.6); "
                + "-fx-font-size: 12px; -fx-border-color: rgba(255,255,255,0.06); -fx-border-radius: 10; "
                + "-fx-control-inner-background: transparent;");

        StackPane previewBox = new StackPane();
        previewBox.setMinHeight(80);
        previewBox.setStyle("-fx-background-color: rgba(255,255,255,0.03); -fx-background-radius: 12; "
                + "-fx-border-color: rgba(255,255,255,0.06); -fx-border-radius: 12;");

        // ---- AI 分析 ----
        Button analyzeBtn = new Button("🚀 AI 自动生成");
        analyzeBtn.getStyleClass().add("btn-primary");
        analyzeBtn.setDisable(true);

        ProgressBar progress = new ProgressBar(-1);
        progress.setVisible(false);

        // ---- 多卡预览列表 ----
        Label cardListTitle = new Label("生成的卡片（勾选后导入）");
        cardListTitle.setStyle("-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 13px;");
        cardListTitle.setVisible(false);

        ListView<CheckBox> cardListView = new ListView<>();
        cardListView.setPrefHeight(180);
        cardListView.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        cardListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(CheckBox item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    setGraphic(item);
                    setStyle("-fx-background-color: rgba(255,255,255,0.04); -fx-padding: 4 8;");
                }
            }
        });
        cardListView.setVisible(false);

        // ---- 底部 ----
        HBox bottomBtns = new HBox(12);
        bottomBtns.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("取消");
        cancelBtn.setStyle("-fx-background-color: rgba(255,255,255,0.06); -fx-background-radius: 10; -fx-text-fill: white; -fx-padding: 8 20; -fx-cursor: hand;");

        Button importBtn = new Button("✅ 导入勾选的卡片");
        importBtn.setStyle("-fx-background-color: #22c55e; -fx-background-radius: 10; -fx-text-fill: white; -fx-padding: 8 20; -fx-cursor: hand; -fx-font-weight: bold;");
        importBtn.setDisable(true);

        bottomBtns.getChildren().addAll(cancelBtn, importBtn);

        root.getChildren().addAll(
                keyLabel, keyField,
                fileTitle, chooseRow, previewBox,
                analyzeBtn, progress,
                cardListTitle, cardListView,
                bottomBtns
        );

        // ---- 事件 ----

        final File[] selectedFile = new File[1];
        final String[] extractedText = new String[1];
        final List<JsonObject> parsedCards = new ArrayList<>();

        chooseBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle(imageMode ? "选择图片" : "选择文件");
            if (imageMode) {
                fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("图片", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"));
            } else {
                fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("文档", "*.txt", "*.docx", "*.pdf"));
            }
            File f = fc.showOpenDialog(dialog);
            if (f == null) return;
            selectedFile[0] = f;
            fileLabel.setText(f.getName());

            if (imageMode || isImage(f)) {
                preview.setImage(new Image(f.toURI().toString()));
                previewBox.getChildren().setAll(preview);
                previewBox.setMinHeight(180);
                textPreview.setText("");
            } else {
                previewBox.getChildren().setAll(textPreview);
                previewBox.setMinHeight(80);
                preview.setImage(null);
                try {
                    String txt = OcrService.extractText(f);
                    extractedText[0] = txt;
                    textPreview.setText(txt.length() > 300 ? txt.substring(0, 300) + "..." : txt);
                } catch (Exception ex) {
                    textPreview.setText("读取失败: " + ex.getMessage());
                }
            }
            cardListView.setVisible(false);
            cardListTitle.setVisible(false);
            importBtn.setDisable(true);
            analyzeBtn.setDisable(keyField.getText().trim().isEmpty());
        });

        keyField.textProperty().addListener((obs, old, val) -> {
            analyzeBtn.setDisable(val.trim().isEmpty() || selectedFile[0] == null);
        });

        analyzeBtn.setOnAction(e -> {
            if (selectedFile[0] == null || keyField.getText().trim().isEmpty()) return;
            PREFS.put("deepseek_api_key", keyField.getText().trim());

            progress.setVisible(true);
            analyzeBtn.setDisable(true);
            parsedCards.clear();

            String apiKey = keyField.getText().trim();
            File file = selectedFile[0];
            String text = extractedText[0];

            new Thread(() -> {
                try {
                    String result;
                    if (imageMode || isImage(file)) {
                        // 【修复】真正把图片 base64 发给 AI 视觉模型
                        byte[] imgBytes = Files.readAllBytes(file.toPath());
                        String base64 = Base64.getEncoder().encodeToString(imgBytes);
                        result = OcrService.analyzeImage(apiKey, base64);
                    } else if (text != null && !text.isEmpty()) {
                        result = OcrService.analyzeText(apiKey, text, file.getName());
                    } else {
                        result = OcrService.analyzeText(apiKey, OcrService.extractText(file), file.getName());
                    }

                    List<JsonObject> cards = OcrService.parseCardList(result);

                    Platform.runLater(() -> {
                        progress.setVisible(false);
                        analyzeBtn.setDisable(false);

                        if (cards.isEmpty()) {
                            showAlert(dialog, "解析失败", "AI 返回的格式无法解析，请重试。");
                            return;
                        }

                        parsedCards.addAll(cards);
                        cardListView.getItems().clear();
                        for (JsonObject obj : cards) {
                            String front = obj.get("front").getAsString();
                            String cat = obj.get("category").getAsString();
                            CheckBox cb = new CheckBox(front + "  [" + cat + "]");
                            cb.setSelected(true);
                            cb.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-padding: 6 0;");
                            cardListView.getItems().add(cb);
                        }

                        cardListTitle.setVisible(true);
                        cardListView.setVisible(true);
                        importBtn.setDisable(false);
                        importBtn.setText("✅ 导入 " + cards.size() + " 张卡片");
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        progress.setVisible(false);
                        analyzeBtn.setDisable(false);
                        showAlert(dialog, "分析失败", ex.getMessage());
                    });
                }
            }).start();
        });

        final List<FlashCard>[] resultList = new List[]{null};
        importBtn.setOnAction(e -> {
            List<FlashCard> selected = new ArrayList<>();
            for (int i = 0; i < cardListView.getItems().size(); i++) {
                if (cardListView.getItems().get(i).isSelected()) {
                    JsonObject obj = parsedCards.get(i);
                    selected.add(new FlashCard(
                            obj.get("front").getAsString(),
                            obj.get("back").getAsString(),
                            obj.get("category").getAsString()
                    ));
                }
            }
            if (selected.isEmpty()) {
                showAlert(dialog, "提示", "请至少勾选一张卡片。");
                return;
            }
            resultList[0] = selected;
            dialog.close();
        });

        cancelBtn.setOnAction(e -> dialog.close());

        Scene scene = new Scene(root, 520, 680);
        scene.setFill(javafx.scene.paint.Color.web("#111827"));
        scene.getStylesheets().add(OcrDialog.class.getResource("/styles/glass.css").toExternalForm());
        dialog.setScene(scene);
        dialog.showAndWait();

        return resultList[0];
    }

    private static void showAlert(Stage owner, String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.getDialogPane().setStyle("-fx-background-color: #1e1b34; -fx-text-fill: white;");
        alert.show();
    }
}
