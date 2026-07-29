package com.hqu.memory.ui;

import com.hqu.memory.model.FlashCard;
import com.hqu.memory.storage.DataStore;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.layout.*;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 卡片列表页：展示所有卡片，支持分类筛选、添加/编辑/删除
 */
public class CardListView {

    private final BorderPane view;
    private final ListView<FlashCard> listView;
    private final ComboBox<String> filterCombo;
    private final ObservableList<FlashCard> masterList;
    private final FilteredList<FlashCard> filteredList;
    private final Button editBtn;
    private final Button deleteBtn;

    public CardListView() {
        masterList = FXCollections.observableArrayList();
        filteredList = new FilteredList<>(masterList, p -> true);
        listView = createListView();
        filterCombo = createFilterCombo();
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

        toolbar.getChildren().addAll(title, spacer, filterCombo, addBtn);

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
        editBtn.setOnAction(e -> handleEdit());
        deleteBtn.setOnAction(e -> handleDelete());
        filterCombo.setOnAction(e -> applyFilter());

        // 列表选中状态变化时切换按钮可用性
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

    /** 从数据源重新加载 */
    public void refresh() {
        List<FlashCard> cards = DataStore.loadCards();
        cards.sort(Comparator.comparing(FlashCard::getNextReviewDate));
        masterList.setAll(cards);

        // 更新筛选下拉
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
                    HBox box = new HBox(12);
                    box.setAlignment(Pos.CENTER_LEFT);

                    Label frontLabel = new Label(card.getFront());
                    frontLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.9); -fx-font-size: 14px;");

                    Label badge = new Label(card.getCategory());
                    badge.getStyleClass().add("category-badge");

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

                    box.getChildren().addAll(frontLabel, badge, spacer, dueLabel);
                    setGraphic(box);
                }
            }
        });
        return lv;
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
        if (selected == null || "全部".equals(selected)) {
            filteredList.setPredicate(p -> true);
        } else {
            filteredList.setPredicate(card -> card.getCategory().equals(selected));
        }
        // 注意：这里我们手动筛选 masterList，而不是用 filteredList 绑定
        // 因为 listView 需要显示 filteredList 但 edit/delete 需要操作原始数据
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
