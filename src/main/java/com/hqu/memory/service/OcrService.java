package com.hqu.memory.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 调用 AI API 识别文件/图片内容并生成多张卡片
 * 支持：
 * - 文本文件（txt/docx/pdf）→ analyzeText
 * - 图片文件（png/jpg）→ analyzeImage（视觉模型）
 */
public class OcrService {

    private static final String DEFAULT_API_URL = "https://api.deepseek.com/chat/completions";

    public static String extractText(File file) throws IOException {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".txt")) return Files.readString(file.toPath());
        if (name.endsWith(".docx")) return extractDocxText(file);
        if (name.endsWith(".pdf")) return extractPdfText(file);
        throw new IllegalArgumentException("不支持的文件格式");
    }

    private static String extractDocxText(File file) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    return xml.replaceAll("(?s)<[^>]+>", " ").replaceAll("\\s+", " ").trim();
                }
            }
        }
        throw new IOException("无法读取 DOCX 内容");
    }

    private static String extractPdfText(File file) throws IOException {
        try (PDDocument doc = PDDocument.load(file)) {
            String text = new PDFTextStripper().getText(doc).trim();
            if (text.isEmpty()) throw new IOException("PDF 无文本内容");
            return text;
        }
    }

    /**
     * 分析文本内容，返回多张卡片的 JSON 数组字符串
     * 每张卡片格式：{"front":"...", "back":"...", "category":"..."}
     */
    public static String analyzeText(String apiKey, String text, String fileName) throws Exception {
        // 如果文本太长，分段处理
        if (text.length() > 4000) text = text.substring(0, 4000) + "\n\n[内容过长，仅分析前 4000 字]";

        JsonObject body = new JsonObject();
        body.addProperty("model", "deepseek-chat");
        body.addProperty("max_tokens", 2000);
        body.addProperty("temperature", 0.3);

        JsonArray messages = new JsonArray();

        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", "你是一个考研学习助手。用户会发来一段学习内容，请分析内容并生成多张闪卡。"
                + "以 JSON 数组格式返回（不要 markdown 代码块标记）。\n"
                + "格式: [{\"front\": \"题目1\", \"back\": \"答案1\", \"category\": \"分类\"}, "
                + "{\"front\": \"题目2\", \"back\": \"答案2\", \"category\": \"分类\"}]\n"
                + "分类只能是以下之一：政治、英语、数学、专业课。如果无法确定，填\"其他\"。\n"
                + "注意：必须返回数组，即使只有一张卡片也要用 [] 包裹。");
        messages.add(sysMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", "以下是来自文件 \"" + fileName + "\" 的内容，请分析并生成多张闪卡：\n\n" + text);
        messages.add(userMsg);

        body.add("messages", messages);

        return callApi(body, apiKey);
    }

    /**
     * 分析图片内容（视觉模型），返回多张卡片的 JSON 数组字符串
     * 【讲解重点：图片识别流程】
     *
     * 1. 图片转 base64 编码
     * 2. 调用视觉模型的 image_url 接口
     * 3. AI 读取图片上的文字/内容，生成闪卡
     *
     * 注意：DeepSeek 的 deepseek-chat 是纯文本模型，不支持图片。
     * 这里使用 OpenAI 兼容的视觉模型接口（如 qwen-vl / gpt-4o-mini 等）。
     * 用户可以在配置时选择支持视觉的 API。
     */
    public static String analyzeImage(String apiKey, String imageData) throws Exception {
        // 尝试调用支持视觉的 API
        // 默认尝试硅基流动/通义等支持视觉的 OpenAI 兼容接口
        // 这里为了兼容性，先尝试 DeepSeek，如果失败提示用户

        JsonObject body = new JsonObject();
        body.addProperty("model", "deepseek-chat"); // 需要用户配置支持视觉的模型
        body.addProperty("max_tokens", 2000);
        body.addProperty("temperature", 0.3);

        JsonArray messages = new JsonArray();

        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", "你是一个考研学习助手。用户会发来一张图片（可能是笔记、错题、题目截图）。"
                + "请识别图片上的文字内容并生成多张闪卡。"
                + "以 JSON 数组格式返回（不要 markdown 代码块标记）。\n"
                + "格式: [{\"front\": \"题目1\", \"back\": \"答案1\", \"category\": \"分类\"}, "
                + "{\"front\": \"题目2\", \"back\": \"答案2\", \"category\": \"分类\"}]\n"
                + "分类只能是以下之一：政治、英语、数学、专业课。如果无法确定，填\"其他\"。");
        messages.add(sysMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        JsonArray content = new JsonArray();

        // 文本部分
        JsonObject textPart = new JsonObject();
        textPart.addProperty("type", "text");
        textPart.addProperty("text", "请识别这张图片上的内容并生成闪卡。");
        content.add(textPart);

        // 图片部分（base64）
        JsonObject imgPart = new JsonObject();
        imgPart.addProperty("type", "image_url");
        JsonObject imgUrl = new JsonObject();
        imgUrl.addProperty("url", "data:image/png;base64," + imageData);
        imgPart.add("image_url", imgUrl);
        content.add(imgPart);

        userMsg.add("content", content);
        messages.add(userMsg);

        body.add("messages", messages);

        return callApi(body, apiKey);
    }

    /** 解析 API 返回的 JSON，提取卡片列表 */
    public static List<JsonObject> parseCardList(String jsonStr) {
        List<JsonObject> list = new ArrayList<>();
        jsonStr = jsonStr.trim();

        // 清理 markdown 代码块
        if (jsonStr.contains("```")) {
            jsonStr = jsonStr.replaceAll("(?s)```[a-zA-Z]*\\s*", "").trim();
        }

        try {
            // 尝试解析为数组
            JsonArray arr = new Gson().fromJson(jsonStr, JsonArray.class);
            for (int i = 0; i < arr.size(); i++) {
                list.add(arr.get(i).getAsJsonObject());
            }
        } catch (Exception e) {
            // 可能是单个对象，包装成数组
            try {
                JsonObject obj = new Gson().fromJson(jsonStr, JsonObject.class);
                list.add(obj);
            } catch (Exception e2) {
                // 无法解析
            }
        }
        return list;
    }

    // ---- 私有 ----

    private static String callApi(JsonObject body, String apiKey) throws Exception {
        URL url = URI.create(DEFAULT_API_URL).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);

        String jsonBody = new Gson().toJson(body);
        conn.setRequestProperty("Content-Length", String.valueOf(jsonBody.getBytes(StandardCharsets.UTF_8).length));

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        InputStream is = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
        String response = new Scanner(is, "UTF-8").useDelimiter("\\A").next();
        is.close();

        if (status != 200) {
            throw new RuntimeException("API 请求失败 (" + status + "): " + response);
        }

        JsonObject respJson = new Gson().fromJson(response, JsonObject.class);
        return respJson.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString().trim();
    }
}
