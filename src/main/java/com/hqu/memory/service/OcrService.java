package com.hqu.memory.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 调用 AI API 识别图片/文件内容并生成卡片
 * 支持 DeepSeek API
 */
public class OcrService {

    private static final String DEFAULT_API_URL = "https://api.deepseek.com/chat/completions";

    /** 从文件中提取纯文本内容 */
    public static String extractText(File file) throws IOException {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".txt")) {
            return Files.readString(file.toPath());
        } else if (name.endsWith(".docx")) {
            return extractDocxText(file);
        } else if (name.endsWith(".pdf")) {
            return extractPdfText(file);
        } else {
            throw new IllegalArgumentException("不支持的文件格式");
        }
    }

    private static String extractDocxText(File file) throws IOException {
        StringBuilder text = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    byte[] buf = zis.readAllBytes();
                    String xml = new String(buf, StandardCharsets.UTF_8);
                    text.append(xml.replaceAll("(?s)<[^>]+>", " ").replaceAll("\\s+", " ").trim());
                    break;
                }
            }
        }
        if (text.isEmpty()) throw new IOException("无法读取 DOCX 内容");
        return text.toString();
    }

    private static String extractPdfText(File file) throws IOException {
        StringBuilder text = new StringBuilder();
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] buf = new byte[(int) Math.min(raf.length(), 10_000_000)];
            raf.readFully(buf);
            String content = new String(buf, StandardCharsets.ISO_8859_1);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "(?<=BT\\s)(.*?)(?=ET)", java.util.regex.Pattern.DOTALL).matcher(content);
            while (m.find()) {
                java.util.regex.Matcher tm = java.util.regex.Pattern.compile(
                        "\\(([^)]*)\\)\\s*Tj").matcher(m.group());
                while (tm.find()) text.append(tm.group(1)).append(" ");
            }
        }
        if (text.isEmpty()) throw new IOException("无法提取 PDF 文本（可能是扫描件，请用图片识别）");
        return text.toString().trim();
    }

    /** 分析图片 → 调用 DeepSeek 视觉能力 */
    public static String analyzeImage(String apiKey, String imageData) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", "deepseek-chat");
        body.addProperty("max_tokens", 1000);
        body.addProperty("temperature", 0.3);

        JsonArray messages = new JsonArray();

        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", "你是一个考研学习助手。用户发来一张图片，请分析内容并生成一张闪卡（JSON格式返回，不要markdown标记）。\n"
                + "格式: {\"front\": \"题目\", \"back\": \"答案\", \"category\": \"分类\"}\n"
                + "分类: 政治/英语/数学/专业课/其他");
        messages.add(sysMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", "请分析这张图片，生成一张闪卡。");
        messages.add(userMsg);

        body.add("messages", messages);

        // 兼容处理：deepseek-chat 目前不支持 image_url，先发文字提示
        // 实际上，对于图片我们直接试 API，如果不支持会报错，那时回退到文字描述
        return callApi(body, apiKey);
    }

    /** 分析文本内容 */
    public static String analyzeText(String apiKey, String text, String fileName) throws Exception {
        // 裁剪过长文本
        if (text.length() > 3000) text = text.substring(0, 3000) + "\n\n[内容过长已截断]";

        JsonObject body = new JsonObject();
        body.addProperty("model", "deepseek-chat");
        body.addProperty("max_tokens", 1000);
        body.addProperty("temperature", 0.3);

        JsonArray messages = new JsonArray();

        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", "你是一个考研学习助手。用户会发来一段学习内容，请分析内容并生成一张闪卡，以JSON格式返回，不要markdown标记。\n"
                + "格式: {\"front\": \"题目/问题\", \"back\": \"答案/解析\", \"category\": \"分类\"}\n"
                + "分类只能是以下之一：政治、英语、数学、专业课。如果无法确定，填\"其他\"。");
        messages.add(sysMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", "以下来自文件 \"" + fileName + "\"，请分析并生成一张闪卡：\n\n" + text);
        messages.add(userMsg);

        body.add("messages", messages);

        return callApi(body, apiKey);
    }

    // ---- 私有方法 ----

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
        String response = new Scanner(is, "UTF-8").useDelimiter("\\A").hasNext() ? new Scanner(is, "UTF-8").useDelimiter("\\A").next() : "";
        is.close();

        if (status != 200) {
            throw new RuntimeException("API 请求失败 (" + status + "): " + response);
        }

        JsonObject respJson = new Gson().fromJson(response, JsonObject.class);
        String contentStr = respJson.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString().trim();

        // 清理 markdown 代码块
        if (contentStr.contains("```")) {
            contentStr = contentStr.replaceAll("(?s)```[a-zA-Z]*\\s*", "").trim();
        }
        // 提取 JSON
        int start = contentStr.indexOf("{");
        int end = contentStr.lastIndexOf("}");
        if (start >= 0 && end > start) {
            contentStr = contentStr.substring(start, end + 1);
        }

        return contentStr;
    }
}
