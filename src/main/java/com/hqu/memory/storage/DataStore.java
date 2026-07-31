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
 * 【讲解重点：如何保存和读取数据】
 *
 * 选择 JSON 文件而不是数据库的原因：
 * 1. 单机应用，数据量小（<1000 张卡片），JSON 足够
 * 2. 零配置，不需要安装数据库
 * 3. JSON 可读，方便调试（可以直接用记事本打开查看）
 * 4. Gson 库一行代码完成序列化/反序列化
 *
 * 数据存储路径：~/.memory-assistant/flashcards.json
 * 这样数据文件和程序分离，删除程序不影响数据
 */
public class DataStore {

    private static final String DATA_DIR = System.getProperty("user.home") + "/.memory-assistant";
    private static final String DATA_FILE = DATA_DIR + "/flashcards.json";

    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()  // 格式化 JSON，方便查看
            .setLenient()
            .create();

    /**
     * 从 JSON 文件加载所有卡片
     * 流程：读文件 → JSON 字符串 → Gson 转 List<FlashCard>
     * 如果文件不存在，返回空列表（首次使用时）
     */
    public static List<FlashCard> loadCards() {
        ensureDataDir();
        File file = new File(DATA_FILE);
        if (!file.exists()) return new ArrayList<>();

        try (Reader reader = new FileReader(file)) {
            // TypeToken 告诉 Gson 要反序列化的类型
            Type type = new TypeToken<List<FlashCard>>() {}.getType();
            List<FlashCard> cards = gson.fromJson(reader, type);
            return cards != null ? cards : new ArrayList<>();
        } catch (IOException e) {
            System.err.println("读取数据失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 将所有卡片保存到 JSON 文件
     * 流程：List<FlashCard> → Gson 转 JSON 字符串 → 写文件
     * 每次修改都全量保存（数据量小，不影响性能）
     */
    public static void saveCards(List<FlashCard> cards) {
        ensureDataDir();
        try (Writer writer = new FileWriter(DATA_FILE)) {
            gson.toJson(cards, writer);
        } catch (IOException e) {
            System.err.println("保存数据失败: " + e.getMessage());
        }
    }

    /** 确保数据目录存在，首次使用时会自动创建 */
    private static void ensureDataDir() {
        File dir = new File(DATA_DIR);
        if (!dir.exists()) dir.mkdirs();
    }
}
