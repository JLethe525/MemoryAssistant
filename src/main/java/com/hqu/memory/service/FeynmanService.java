package com.hqu.memory.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * 费曼学习法 AI 对话服务
 * 维护对话历史，AI 扮演学生追问
 */
public class FeynmanService {

    private static final String API_URL = "https://api.deepseek.com/chat/completions";
    private final String apiKey;
    private final JsonArray messages;

    public FeynmanService(String apiKey, String topic, String correctAnswer) {
        this.apiKey = apiKey;
        this.messages = new JsonArray();

        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", "你是一个费曼学习法的陪练。用户正在学习考研知识，他会向你解释一个概念。"
                + "你需要扮演一个聪明的学生：\n"
                + "1. 如果用户解释得清晰正确，就表示听懂了并提出一个追问\n"
                + "2. 如果用户解释得模糊或错误，就提出质疑和反问\n"
                + "3. 不要直接给出正确答案，而是通过提问引导用户自己思考\n"
                + "4. 每次回复控制在 50 字以内\n"
                + "5. 当用户彻底讲明白后，回复【理解】开头并总结\n\n"
                + "正确的知识点如下（用于判断用户是否讲对）：\n" + correctAnswer);
        messages.add(sys);

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", "我开始解释了：" + topic);
        messages.add(user);
    }

    /** 发送用户的消息并获取 AI 回复 */
    public String chat(String userInput) throws Exception {
        // 添加用户消息
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userInput);
        messages.add(userMsg);

        // 构建请求
        JsonObject body = new JsonObject();
        body.addProperty("model", "deepseek-chat");
        body.addProperty("max_tokens", 300);
        body.addProperty("temperature", 0.7);
        body.add("messages", messages);

        URL url = URI.create(API_URL).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        String jsonBody = new Gson().toJson(body);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        Scanner sc = new Scanner(status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream(), "UTF-8");
        String resp = sc.useDelimiter("\\A").hasNext() ? sc.next() : "";
        sc.close();

        if (status != 200) throw new RuntimeException("API 失败 (" + status + "): " + resp);

        JsonObject respJson = new Gson().fromJson(resp, JsonObject.class);
        String reply = respJson.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString().trim();

        // 保存 AI 回复
        JsonObject aiMsg = new JsonObject();
        aiMsg.addProperty("role", "assistant");
        aiMsg.addProperty("content", reply);
        messages.add(aiMsg);

        return reply;
    }
}
