package com.hqu.memory.ui;

import com.hqu.memory.model.FlashCard;
import com.hqu.memory.storage.DataStore;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 卡片列表页：展示所有卡片，支持搜索、分类筛选、收藏、添加/编辑/删除
 */
public class CardListView {

    private final BorderPane view;
    private final ListView<FlashCard> listView;
    private final ComboBox<String> filterCombo;
    private final TextField searchField;
    private final ToggleButton starFilterBtn;
    private final ObservableList<FlashCard> masterList;
    private final FilteredList<FlashCard> filteredList;
    private final Button editBtn;
    private final Button deleteBtn;

    private final Map<String, String> categoryColors = new LinkedHashMap<>();
    private static final String[] CAT_PALETTE = {
            "#3b82f6", "#ef4444", "#22c55e", "#f59e0b",
            "#a855f7", "#ec4899", "#06b6d4", "#f97316",
            "#14b8a6", "#8b5cf6", "#84cc16", "#f43f5e"
    };

    public CardListView() {
        masterList = FXCollections.observableArrayList();
        filteredList = new FilteredList<>(masterList, p -> true);
        listView = createListView();

        searchField = new TextField();
        searchField.setPromptText("搜索卡片内容...");
        searchField.getStyleClass().add("glass-textarea");
        searchField.setPrefWidth(200);

        filterCombo = createFilterCombo();
        starFilterBtn = new ToggleButton("⭐ 只看收藏");
        starFilterBtn.setStyle("-fx-background-color: rgba(255,255,255,0.06); -fx-background-radius: 10; "
                + "-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 12px; -fx-padding: 8 14; -fx-cursor: hand; "
                + "-fx-border-color: rgba(255,255,255,0.08); -fx-border-radius: 10;");

        editBtn = new Button("编辑");
        deleteBtn = new Button("删除");
        styleButtons();

        view = new BorderPane();
        view.getStyleClass().add("glass-panel");

        // 顶部工具栏
        HBox toolbar = new HBox(12);
        toolbar.setPadding(new Insets(0, 0, 16, 0));
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("卡片管理");
        title.getStyleClass().add("page-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addBtn = new Button("+ 添加卡片");
        addBtn.getStyleClass().add("btn-primary");

        Button scanBtn = new Button("📷 识图生成");
        scanBtn.setStyle("-fx-background-color: #10b981; -fx-background-radius: 12; -fx-text-fill: white; "
                + "-fx-font-size: 13px; -fx-padding: 10 18; -fx-cursor: hand; -fx-font-weight: bold;");

        toolbar.getChildren().addAll(title, spacer, searchField, filterCombo, starFilterBtn, scanBtn, addBtn);

        // 底部操作栏
        HBox bottomBar = new HBox(10);
        bottomBar.setPadding(new Insets(16, 0, 0, 0));
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        Label countLabel = new Label();
        countLabel.getStyleClass().add("stat-label");
        Region bottomSpacer = new Region();
        HBox.setHgrow(bottomSpacer, Priority.ALWAYS);
        bottomBar.getChildren().addAll(countLabel, bottomSpacer, editBtn, deleteBtn);

        view.setTop(toolbar);
        view.setCenter(listView);
        view.setBottom(bottomBar);

        // 事件绑定
        addBtn.setOnAction(e -> handleAdd());
        scanBtn.setOnAction(e -> handleScan());
        editBtn.setOnAction(e -> handleEdit());
        deleteBtn.setOnAction(e -> handleDelete());
        filterCombo.setOnAction(e -> applyFilter());
        searchField.textProperty().addListener((obs, old, val) -> applyFilter());
        starFilterBtn.setOnAction(e -> applyFilter());

        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean hasSel = sel != null;
            editBtn.setDisable(!hasSel);
            deleteBtn.setDisable(!hasSel);
        });
        editBtn.setDisable(true);
        deleteBtn.setDisable(true);

        refresh();
    }

    public BorderPane getView() { return view; }

    public void refresh() {
        List<FlashCard> cards = DataStore.loadCards();
        cards.sort(Comparator.comparing(FlashCard::getNextReviewDate));
        masterList.setAll(cards);

        int idx = 0;
        for (String cat : cards.stream().map(FlashCard::getCategory).distinct().sorted().collect(Collectors.toList())) {
            if (!categoryColors.containsKey(cat)) {
                categoryColors.put(cat, CAT_PALETTE[idx % CAT_PALETTE.length]);
                idx++;
            }
        }

        String currentFilter = filterCombo.getValue();
        filterCombo.getItems().clear();
        filterCombo.getItems().add("全部");
        filterCombo.getItems().addAll(
                cards.stream().map(FlashCard::getCategory).distinct().sorted().collect(Collectors.toList())
        );
        if (currentFilter != null && filterCombo.getItems().contains(currentFilter)) {
            filterCombo.setValue(currentFilter);
        } else {
            filterCombo.setValue("全部");
        }
        applyFilter();
    }

    // ---------- 私有方法 ----------

    private ListView<FlashCard> createListView() {
        ListView<FlashCard> lv = new ListView<>();
        lv.getStyleClass().add("card-list");
        lv.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(FlashCard card, boolean empty) {
                super.updateItem(card, empty);
                if (empty || card == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox box = new HBox(10);
                    box.setAlignment(Pos.CENTER_LEFT);

                    // 收藏星标
                    Label starLabel = new Label("⭐");
                    starLabel.setStyle(card.isStarred()
                            ? "-fx-font-size: 17px; -fx-cursor: hand; -fx-padding: 0 0 0 4; -fx-text-fill: #fbbf24;"
                            : "-fx-font-size: 17px; -fx-cursor: hand; -fx-padding: 0 0 0 4; -fx-text-fill: rgba(255,255,255,0.15);");
                    starLabel.setOnMouseClicked(e -> toggleStar(card));

                    Label frontLabel = new Label(card.getFront());
                    frontLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.9); -fx-font-size: 14px;");

                    Label badge = new Label(card.getCategory());
                    String catColor = categoryColors.getOrDefault(card.getCategory(), "#6366f1");
                    badge.setStyle("-fx-background-color: " + catColor + "33; "
                            + "-fx-background-radius: 6; -fx-text-fill: white; -fx-font-size: 11px; "
                            + "-fx-padding: 2 8; -fx-border-color: " + catColor + "55; -fx-border-radius: 6;");

                    String statsStr = "✓" + card.getEasyCount() + " · ?" + card.getMediumCount() + " · ✗" + card.getHardCount();
                    Label statsLabel = new Label(statsStr);
                    statsLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.35); -fx-font-size: 11px; -fx-padding: 0 8 0 0;");

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    Label dueLabel = new Label();
                    if (card.isDue(java.time.LocalDate.now())) {
                        dueLabel.setText("待复习");
                        dueLabel.setStyle("-fx-text-fill: #f5576c; -fx-font-size: 12px;");
                    } else {
                        dueLabel.setText("下次: " + card.getNextReviewDate());
                        dueLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.4); -fx-font-size: 12px;");
                    }

                    box.getChildren().addAll(starLabel, frontLabel, badge, statsLabel, spacer, dueLabel);
                    setGraphic(box);
                }
            }
        });
        return lv;
    }

    private void toggleStar(FlashCard card) {
        card.setStarred(!card.isStarred());
        List<FlashCard> cards = DataStore.loadCards();
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).getId().equals(card.getId())) {
                cards.set(i, card);
                break;
            }
        }
        DataStore.saveCards(cards);
        refresh();
    }

    private ComboBox<String> createFilterCombo() {
        ComboBox<String> combo = new ComboBox<>();
        combo.getStyleClass().add("glass-combo");
        combo.setPromptText("筛选分类");
        return combo;
    }

    private void styleButtons() {
        editBtn.getStyleClass().add("btn-sm");
        deleteBtn.getStyleClass().add("btn-danger");
    }

    private void applyFilter() {
        String selected = filterCombo.getValue();
        String keyword = searchField.getText().toLowerCase().trim();
        boolean onlyStarred = starFilterBtn.isSelected();

        filteredList.setPredicate(card -> {
            // 分类筛选
            if (selected != null && !"全部".equals(selected) && !card.getCategory().equals(selected)) return false;
            // 收藏筛选
            if (onlyStarred && !card.isStarred()) return false;
            // 搜索关键词
            if (!keyword.isEmpty()) {
                return card.getFront().toLowerCase().contains(keyword)
                        || card.getBack().toLowerCase().contains(keyword);
            }
            return true;
        });
        listView.setItems(filteredList);
    }

    private void handleAdd() {
        List<String> cats = masterList.stream().map(FlashCard::getCategory).distinct().collect(Collectors.toList());
        FlashCard newCard = CardFormDialog.show(null, cats);
        if (newCard != null) {
            List<FlashCard> cards = DataStore.loadCards();
            cards.add(newCard);
            DataStore.saveCards(cards);
            refresh();
        }
    }

    private void handleScan() {
        List<String> cats = masterList.stream().map(FlashCard::getCategory).distinct().collect(Collectors.toList());
        FlashCard newCard = OcrDialog.show(cats);
        if (newCard != null) {
            List<FlashCard> cards = DataStore.loadCards();
            cards.add(newCard);
            DataStore.saveCards(cards);
            refresh();
        }
    }

    private void handleEdit() {
        FlashCard selected = listView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        List<String> cats = masterList.stream().map(FlashCard::getCategory).distinct().collect(Collectors.toList());
        FlashCard updated = CardFormDialog.show(selected, cats);
        if (updated != null) {
            List<FlashCard> cards = DataStore.loadCards();
            for (int i = 0; i < cards.size(); i++) {
                if (cards.get(i).getId().equals(updated.getId())) {
                    cards.set(i, updated);
                    break;
                }
            }
            DataStore.saveCards(cards);
            refresh();
        }
    }

    private void handleDelete() {
        FlashCard selected = listView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "确定删除这张卡片吗？\n\n" + selected.getFront(),
                ButtonType.YES, ButtonType.NO);
        alert.getDialogPane().getStylesheets().add(
                getClass().getResource("/styles/glass.css").toExternalForm()
        );
        alert.getDialogPane().getStyleClass().add("dialog-pane");
        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                List<FlashCard> cards = DataStore.loadCards();
                cards.removeIf(c -> c.getId().equals(selected.getId()));
                DataStore.saveCards(cards);
                refresh();
            }
        });
    }
}
