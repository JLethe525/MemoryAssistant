package com.hqu.memory.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hqu.memory.model.FlashCard;

import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据持久化层
 * 将卡片数据以 JSON 格式保存到 ~/.memory-assistant/flashcards.json
 */
public class DataStore {

    private static final String DATA_DIR = System.getProperty("user.home") + "/.memory-assistant";
    private static final String DATA_FILE = DATA_DIR + "/flashcards.json";

    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .setLenient()
            .create();

    /** 从 JSON 文件加载所有卡片 */
    public static List<FlashCard> loadCards() {
        ensureDataDir();
        File file = new File(DATA_FILE);
        if (!file.exists()) return new ArrayList<>();

        try (Reader reader = new FileReader(file)) {
            Type type = new TypeToken<List<FlashCard>>() {}.getType();
            List<FlashCard> cards = gson.fromJson(reader, type);
            return cards != null ? cards : new ArrayList<>();
        } catch (IOException e) {
            System.err.println("读取数据失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /** 将所有卡片保存到 JSON 文件 */
    public static void saveCards(List<FlashCard> cards) {
        ensureDataDir();
        try (Writer writer = new FileWriter(DATA_FILE)) {
            gson.toJson(cards, writer);
        } catch (IOException e) {
            System.err.println("保存数据失败: " + e.getMessage());
        }
    }

    /** 确保数据目录存在 */
    private static void ensureDataDir() {
        File dir = new File(DATA_DIR);
        if (!dir.exists()) dir.mkdirs();
    }
}
