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
 * 调用 AI API 识别文件内容并生成多张卡片
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
