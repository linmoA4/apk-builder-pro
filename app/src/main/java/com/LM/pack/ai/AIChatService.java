package com.LM.pack.ai;

import com.LM.pack.BuildConfig;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

public class AIChatService {

    public interface ChatCallback {
        void onSuccess(String content);
        void onError(String message);
    }

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;

    public AIChatService() {
        httpClient = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
            .build();
    }

    public void requestChat(String model, String systemPrompt, String userContent, final ChatCallback callback) {
        if (callback == null) {
            return;
        }
        if (safeText(BuildConfig.BIGMODEL_API_KEY).length() == 0) {
            callback.onError("AI Key 未配置");
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("model", safeText(model));
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", safeText(systemPrompt)));
            messages.put(new JSONObject().put("role", "user").put("content", safeText(userContent)));
            body.put("messages", messages);

            Request request = new Request.Builder()
                .url(BuildConfig.BIGMODEL_API_URL)
                .addHeader("Authorization", "Bearer " + BuildConfig.BIGMODEL_API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(JSON_MEDIA_TYPE, body.toString()))
                .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("AI 请求失败：" + safeText(e == null ? null : e.getMessage(), "网络异常"));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String rawBody = response.body() == null ? "" : response.body().string();
                    if (!response.isSuccessful()) {
                        callback.onError("AI 请求失败：" + response.code() + " " + safeText(rawBody, "接口返回异常"));
                        return;
                    }
                    try {
                        JSONObject body = new JSONObject(rawBody);
                        JSONArray choices = body.optJSONArray("choices");
                        if (choices == null || choices.length() == 0) {
                            callback.onError("AI 响应中没有可用内容");
                            return;
                        }
                        JSONObject message = choices.optJSONObject(0) == null ? null : choices.optJSONObject(0).optJSONObject("message");
                        String content = extractContent(message);
                        if (safeText(content).length() == 0) {
                            callback.onError("AI 返回了空内容");
                            return;
                        }
                        callback.onSuccess(content.trim());
                    } catch (Exception parseException) {
                        callback.onError("AI 响应解析失败：" + safeText(parseException.getMessage(), "未知格式"));
                    }
                }
            });
        } catch (Exception e) {
            callback.onError("AI 请求准备失败：" + safeText(e.getMessage(), "未知异常"));
        }
    }

    private String extractContent(JSONObject message) {
        if (message == null) {
            return "";
        }
        Object content = message.opt("content");
        if (content instanceof String) {
            return (String) content;
        }
        if (content instanceof JSONArray) {
            JSONArray array = (JSONArray) content;
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                Object part = array.opt(i);
                if (part instanceof JSONObject) {
                    JSONObject object = (JSONObject) part;
                    String text = object.optString("text", "");
                    if (text.length() > 0) {
                        if (builder.length() > 0) {
                            builder.append('\n');
                        }
                        builder.append(text);
                    }
                } else if (part != null) {
                    if (builder.length() > 0) {
                        builder.append('\n');
                    }
                    builder.append(String.valueOf(part));
                }
            }
            return builder.toString();
        }
        return content == null ? "" : String.valueOf(content);
    }

    private String safeText(String value) {
        return safeText(value, "");
    }

    private String safeText(String value, String fallback) {
        if (value == null) {
            return fallback == null ? "" : fallback;
        }
        String trimmed = value.trim();
        return trimmed.length() == 0 ? (fallback == null ? "" : fallback) : trimmed;
    }
}
