package com.hqu.memory.ui;

import com.hqu.memory.model.FlashCard;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * 添加/编辑卡片的弹窗
 * 共用同一个对话框，编辑时传入已有卡片预填，新建时传入 null
 */
public class CardFormDialog {

    public static FlashCard show(FlashCard existing, List<String> categories) {
        Dialog<FlashCard> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "添加卡片" : "编辑卡片");
        dialog.getDialogPane().getStylesheets().add(
                CardFormDialog.class.getResource("/styles/glass.css").toExternalForm()
        );
        dialog.getDialogPane().getStyleClass().add("dialog-pane");

        // 输入控件
        TextArea frontInput = new TextArea();
        frontInput.setPromptText("输入题目内容...");
        frontInput.setWrapText(true);
        frontInput.setPrefRowCount(3);
        frontInput.getStyleClass().add("glass-textarea");

        TextArea backInput = new TextArea();
        backInput.setPromptText("输入答案内容...");
        backInput.setWrapText(true);
        backInput.setPrefRowCount(5);
        backInput.getStyleClass().add("glass-textarea");

        ComboBox<String> categoryChoice = new ComboBox<>();
        categoryChoice.setEditable(true);
        categoryChoice.setPromptText("选择或输入分类");
        categoryChoice.getItems().addAll(categories);
        categoryChoice.getStyleClass().add("glass-combo");

        // 编辑时预填
        if (existing != null) {
            frontInput.setText(existing.getFront());
            backInput.setText(existing.getBack());
            categoryChoice.setValue(existing.getCategory());
        }

        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.getChildren().addAll(
                new Label("正面（题目）"),
                frontInput,
                new Label("背面（答案）"),
                backInput,
                new Label("分类"),
                categoryChoice
        );

        // 给 label 加样式
        content.getChildren().stream()
                .filter(n -> n instanceof Label)
                .map(n -> (Label) n)
                .forEach(lb -> lb.setStyle("-fx-text-fill: rgba(255,255,255,0.8); -fx-font-size: 13px;"));

        dialog.getDialogPane().setContent(content);

        ButtonType saveBtn = new ButtonType("保存", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        // 保存按钮样式
        dialog.getDialogPane().lookupButton(saveBtn).setStyle(
                "-fx-background-color: #667eea;" +
                "-fx-text-fill: white; -fx-background-radius: 10; -fx-padding: 8 20; -fx-cursor: hand;"
        );

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                String front = frontInput.getText().trim();
                String back = backInput.getText().trim();
                String cat = categoryChoice.getValue();
                if (front.isEmpty() || back.isEmpty() || cat == null || cat.isEmpty()) {
                    return null;
                }
                if (existing != null) {
                    existing.setFront(front);
                    existing.setBack(back);
                    existing.setCategory(cat);
                    return existing;
                } else {
                    return new FlashCard(front, back, cat);
                }
            }
            return null;
        });

        return dialog.showAndWait().orElse(null);
    }
}
