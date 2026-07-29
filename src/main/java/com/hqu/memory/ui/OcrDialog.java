package com.hqu.memory.ui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hqu.memory.model.FlashCard;
import com.hqu.memory.service.OcrService;
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
import java.util.Base64;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * 识图/识文件导入卡片弹窗
 * 支持图片（png/jpg）、文档（txt/docx/pdf）→ AI 分析 → 预览 → 确认导入
 */
public class OcrDialog {

    private static final Preferences PREFS = Preferences.userNodeForPackage(OcrDialog.class);

    /** 判断是否为图片文件 */
    private static boolean isImage(File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".bmp") || n.endsWith(".gif");
    }

    public static FlashCard show(List<String> categories) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("📤 导入文件生成卡片");

        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #111827;");

        // ---- API Key ----
        Label keyLabel = new Label("API Key（DeepSeek）");
        keyLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 13px;");

        PasswordField keyField = new PasswordField();
        keyField.setPromptText("输入你的 DeepSeek API Key");
        String savedKey = PREFS.get("deepseek_api_key", "");
        if (!savedKey.isEmpty()) keyField.setText(savedKey);
        keyField.setStyle("-fx-background-color: rgba(255,255,255,0.06); -fx-background-radius: 10; "
                + "-fx-text-fill: white; -fx-font-size: 13px; -fx-padding: 10 14; -fx-border-color: rgba(255,255,255,0.1); -fx-border-radius: 10;");

        // ---- 选择文件 ----
        Label fileTitle = new Label("选择文件（图片 / TXT / DOCX / PDF）");
        fileTitle.setStyle("-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 13px;");

        Button chooseBtn = new Button("选择文件");
        chooseBtn.setStyle("-fx-background-color: #6366f1; -fx-background-radius: 10; -fx-text-fill: white; -fx-padding: 8 18; -fx-cursor: hand;");

        Label fileLabel = new Label("未选择文件");
        fileLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.35); -fx-font-size: 12px;");

        HBox chooseRow = new HBox(12);
        chooseRow.setAlignment(Pos.CENTER_LEFT);
        chooseRow.getChildren().addAll(chooseBtn, fileLabel);

        // 图片预览（仅图片时显示）
        ImageView preview = new ImageView();
        preview.setFitWidth(300);
        preview.setFitHeight(180);
        preview.setPreserveRatio(true);

        // 文字内容预览（仅文档时显示）
        TextArea textPreview = new TextArea();
        textPreview.setWrapText(true);
        textPreview.setEditable(false);
        textPreview.setPrefRowCount(5);
        textPreview.setStyle("-fx-background-color: rgba(255,255,255,0.04); -fx-text-fill: rgba(255,255,255,0.6); "
                + "-fx-font-size: 12px; -fx-border-color: rgba(255,255,255,0.06); -fx-border-radius: 10; "
                + "-fx-control-inner-background: transparent;");

        StackPane previewBox = new StackPane();
        previewBox.setMinHeight(100);
        previewBox.setStyle("-fx-background-color: rgba(255,255,255,0.03); -fx-background-radius: 12; "
                + "-fx-border-color: rgba(255,255,255,0.06); -fx-border-radius: 12;");

        // ---- AI 分析按钮 ----
        Button analyzeBtn = new Button("🚀 AI 分析生成");
        analyzeBtn.getStyleClass().add("btn-primary");
        analyzeBtn.setDisable(true);

        ProgressBar progress = new ProgressBar(-1);
        progress.setVisible(false);
        progress.setPrefWidth(300);

        // 结果预览
        Label resultFront = new Label();
        resultFront.setWrapText(true);
        resultFront.setStyle("-fx-text-fill: rgba(255,255,255,0.9); -fx-font-size: 14px;");

        Label resultBack = new Label();
        resultBack.setWrapText(true);
        resultBack.setStyle("-fx-text-fill: rgba(255,255,255,0.9); -fx-font-size: 14px;");

        Label resultCat = new Label();
        resultCat.setStyle("-fx-text-fill: rgba(255,255,255,0.6); -fx-font-size: 13px;");

        VBox resultBox = new VBox(8);
        resultBox.setPadding(new Insets(12));
        resultBox.setStyle("-fx-background-color: rgba(255,255,255,0.04); -fx-background-radius: 12; "
                + "-fx-border-color: rgba(255,255,255,0.08); -fx-border-radius: 12;");
        resultBox.setVisible(false);
        resultBox.getChildren().addAll(
                new Label("✨ 生成结果") {{ setStyle("-fx-text-fill: rgba(255,255,255,0.5); -fx-font-size: 12px;"); }},
                resultFront, resultBack, resultCat
        );

        // ---- 底部按钮 ----
        HBox bottomBtns = new HBox(12);
        bottomBtns.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("取消");
        cancelBtn.setStyle("-fx-background-color: rgba(255,255,255,0.06); -fx-background-radius: 10; -fx-text-fill: white; -fx-padding: 8 20; -fx-cursor: hand;");

        Button confirmBtn = new Button("✅ 确认导入");
        confirmBtn.setStyle("-fx-background-color: #22c55e; -fx-background-radius: 10; -fx-text-fill: white; -fx-padding: 8 20; -fx-cursor: hand; -fx-font-weight: bold;");
        confirmBtn.setDisable(true);

        bottomBtns.getChildren().addAll(cancelBtn, confirmBtn);

        root.getChildren().addAll(
                keyLabel, keyField,
                fileTitle, chooseRow, previewBox,
                analyzeBtn, progress, resultBox,
                bottomBtns
        );

        // ---- 事件 ----

        final File[] selectedFile = new File[1];
        final String[] extractedText = new String[1]; // 文档内容

        chooseBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("选择图片或文档");
            fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("支持的文件", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif", "*.txt", "*.docx", "*.pdf"),
                    new FileChooser.ExtensionFilter("图片", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"),
                    new FileChooser.ExtensionFilter("文档", "*.txt", "*.docx", "*.pdf")
            );
            File f = fc.showOpenDialog(dialog);
            if (f != null) {
                selectedFile[0] = f;
                fileLabel.setText(f.getName());

                if (isImage(f)) {
                    // 图片模式
                    preview.setImage(new Image(f.toURI().toString()));
                    previewBox.getChildren().setAll(preview);
                    previewBox.setMinHeight(180);
                    textPreview.setText("");
                } else {
                    // 文档模式
                    previewBox.getChildren().setAll(textPreview);
                    previewBox.setMinHeight(100);
                    preview.setImage(null);
                    try {
                        String txt = OcrService.extractText(f);
                        extractedText[0] = txt;
                        textPreview.setText(txt.length() > 500 ? txt.substring(0, 500) + "..." : txt);
                    } catch (Exception ex) {
                        textPreview.setText("读取失败: " + ex.getMessage());
                        extractedText[0] = null;
                    }
                }
                analyzeBtn.setDisable(keyField.getText().trim().isEmpty());
            }
        });

        keyField.textProperty().addListener((obs, old, val) -> {
            boolean ready = !val.trim().isEmpty() && selectedFile[0] != null;
            analyzeBtn.setDisable(!ready);
        });

        final String[] aiResult = new String[1];
        analyzeBtn.setOnAction(e -> {
            if (selectedFile[0] == null || keyField.getText().trim().isEmpty()) return;
            PREFS.put("deepseek_api_key", keyField.getText().trim());

            progress.setVisible(true);
            analyzeBtn.setDisable(true);
            resultBox.setVisible(false);
            confirmBtn.setDisable(true);

            String apiKey = keyField.getText().trim();
            File file = selectedFile[0];

            new Thread(() -> {
                try {
                    String result;
                    if (isImage(file)) {
                        byte[] imgBytes = Files.readAllBytes(file.toPath());
                        String base64 = Base64.getEncoder().encodeToString(imgBytes);
                        result = OcrService.analyzeImage(apiKey, base64);
                    } else {
                        String text;
                        if (extractedText[0] != null && !extractedText[0].isEmpty()) {
                            text = extractedText[0];
                        } else {
                            text = OcrService.extractText(file);
                        }
                        result = OcrService.analyzeText(apiKey, text, file.getName());
                    }

                    javafx.application.Platform.runLater(() -> {
                        progress.setVisible(false);
                        analyzeBtn.setDisable(false);

                        try {
                            JsonObject json = new Gson().fromJson(result, JsonObject.class);
                            String front = json.get("front").getAsString();
                            String back = json.get("back").getAsString();
                            String cat = json.get("category").getAsString();

                            resultFront.setText("📌 " + front);
                            resultBack.setText("📝 " + back);
                            resultCat.setText("🏷️ 分类: " + cat);
                            resultBox.setVisible(true);

                            aiResult[0] = result;
                            confirmBtn.setDisable(false);
                        } catch (Exception ex) {
                            resultFront.setText("解析失败，请重试");
                            resultFront.setStyle("-fx-text-fill: #ef4444;");
                            resultBox.setVisible(true);
                        }
                    });
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> {
                        progress.setVisible(false);
                        analyzeBtn.setDisable(false);
                        resultFront.setText("❌ 分析失败: " + ex.getMessage());
                        resultFront.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12px;");
                        resultBox.setVisible(true);
                    });
                }
            }).start();
        });

        final FlashCard[] resultCard = new FlashCard[1];
        confirmBtn.setOnAction(e -> {
            if (aiResult[0] == null) return;
            try {
                JsonObject json = new Gson().fromJson(aiResult[0], JsonObject.class);
                resultCard[0] = new FlashCard(
                        json.get("front").getAsString(),
                        json.get("back").getAsString(),
                        json.get("category").getAsString()
                );
                dialog.close();
            } catch (Exception ex) {
                resultFront.setText("解析失败: " + ex.getMessage());
                resultFront.setStyle("-fx-text-fill: #ef4444;");
            }
        });

        cancelBtn.setOnAction(e -> dialog.close());

        Scene scene = new Scene(root, 480, 700);
        dialog.setScene(scene);
        dialog.showAndWait();

        return resultCard[0];
    }
}
