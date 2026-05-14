package com.xhs.rewriter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhs.rewriter.domain.Note;
import com.xhs.rewriter.web.dto.RewriteRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiRewriteService {
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();
    private final String deepSeekApiKey;
    private final String deepSeekBaseUrl;
    private final String deepSeekModel;

    public AiRewriteService(
            ObjectMapper objectMapper,
            @Value("${app.deepseek.api-key:}") String deepSeekApiKey,
            @Value("${app.deepseek.base-url:https://api.deepseek.com/chat/completions}") String deepSeekBaseUrl,
            @Value("${app.deepseek.model:deepseek-chat}") String deepSeekModel
    ) {
        this.objectMapper = objectMapper;
        this.deepSeekApiKey = deepSeekApiKey;
        this.deepSeekBaseUrl = deepSeekBaseUrl;
        this.deepSeekModel = deepSeekModel;
    }

    public String analyze(Note note) {
        int titleScore = scoreTitle(note.getTitle());
        int contentScore = Math.min(95, 45 + safe(note.getOriginalContent()).length() / 8);
        int interactionScore = Math.min(100, (int) (safeInt(note.getLikeCount()) * 0.03
                + safeInt(note.getCollectCount()) * 0.04
                + safeInt(note.getCommentCount()) * 0.3
                + safeInt(note.getShareCount()) * 0.2));
        int tagScore = parseTags(note.getTagsJson()).isEmpty() ? 45 : 82;
        int imageScore = parseJsonArray(note.getImageUrlsJson()).isEmpty() ? 42 : 78;
        int total = (titleScore + contentScore + interactionScore + tagScore + imageScore) / 5;
        note.setInteractionScore(total);
        note.setPotentialLabel(total >= 80 ? "高潜力" : total >= 60 ? "中等" : "待优化");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("titleScore", titleScore);
        data.put("contentScore", contentScore);
        data.put("interactionScore", interactionScore);
        data.put("tagScore", tagScore);
        data.put("imageScore", imageScore);
        data.put("totalScore", total);
        data.put("potentialLabel", note.getPotentialLabel());
        data.put("keywords", Arrays.asList("种草", "体验", "步骤", "收藏"));
        data.put("suggestions", Arrays.asList(
                "标题可以加入数字或结果型表达，提升点击动机",
                "正文建议按场景、步骤、避坑、总结组织，增强收藏价值",
                "结尾加入评论问题，提升互动率"
        ));
        return toJson(data);
    }

    public String rewrite(Note note, RewriteRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("source", buildSource(note));
        data.put("titleRewrite", runTask(note, request.getTitlePrompt(), "笔记标题 AI 重写"));
        data.put("contentRewrite", runTask(note, request.getContentPrompt(), "笔记内容 AI 重写"));
        data.put("sentimentAnalysis", runTask(note, request.getSentimentPrompt(), "笔记情感分析"));
        data.put("copyAnalysis", runTask(note, request.getCopyPrompt(), "笔记文案分析"));
        return toJson(data);
    }

    public String generateAnalysis(Note note, RewriteRequest request, String existingResultsJson) {
        Map<String, Object> data = parseResultMap(existingResultsJson);
        data.put("source", buildSource(note));
        data.put("sentimentAnalysis", runTask(note, request.getSentimentPrompt(), "笔记情感分析"));
        data.put("copyAnalysis", runTask(note, request.getCopyPrompt(), "笔记文案分析"));
        return toJson(data);
    }

    public String generateRewrite(Note note, RewriteRequest request, String existingResultsJson) {
        Map<String, Object> data = parseResultMap(existingResultsJson);
        data.put("source", buildSource(note));
        data.put("titleRewrite", runTask(note, request.getTitlePrompt(), "笔记标题 AI 重写"));
        data.put("contentRewrite", runTask(note, request.getContentPrompt(), "笔记内容 AI 重写"));
        return toJson(data);
    }

    private Map<String, Object> parseResultMap(String json) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (!StringUtils.hasText(json)) {
            return data;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.isObject()) {
                root.fields().forEachRemaining(entry -> {
                    JsonNode value = entry.getValue();
                    data.put(entry.getKey(), value.isTextual() ? value.asText() : objectMapper.convertValue(value, Object.class));
                });
            }
        } catch (Exception ignored) {
            // Old malformed AI result JSON should not block generating fresh partial results.
        }
        return data;
    }

    private String runTask(Note note, String prompt, String taskName) {
        if (!StringUtils.hasText(deepSeekApiKey)) {
            throw new IllegalStateException("DeepSeek API Key 未配置，无法生成" + taskName);
        }
        if (!StringUtils.hasText(prompt)) {
            throw new IllegalStateException(taskName + "的提示词为空，无法调用 DeepSeek");
        }
        String result;
        try {
            result = callDeepSeek(prompt, note);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("DeepSeek 调用失败：" + ex.getMessage(), ex);
        }
        if (!StringUtils.hasText(result)) {
            throw new IllegalStateException("DeepSeek 未返回" + taskName + "内容");
        }
        return result;
    }

    private String callDeepSeek(String prompt, Note note) {
        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt + "\n\n请基于以下小红书原始笔记信息处理：\n" + buildSourceText(note));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", deepSeekModel);
        body.put("messages", Arrays.asList(userMessage));
        body.put("temperature", 0.7);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(deepSeekApiKey);

        String response = restTemplate.postForObject(deepSeekBaseUrl, new HttpEntity<>(body, headers), String.class);
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isTextual() && StringUtils.hasText(content.asText())) {
                return content.asText();
            }
        } catch (Exception ex) {
            throw new IllegalStateException("DeepSeek 响应解析失败", ex);
        }
        throw new IllegalStateException("DeepSeek 响应为空");
    }

    private Map<String, Object> buildSource(Note note) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("fetchedAt", safe(note.getFetchedAt()));
        source.put("title", safe(note.getTitle()));
        source.put("content", safe(note.getOriginalContent()));
        source.put("topics", parseTags(note.getTagsJson()));
        source.put("authorName", safe(note.getAuthorName()));
        source.put("authorSignature", blankAsNone(note.getAuthorSignature()));
        source.put("publishTime", safe(note.getPublishTime()));
        source.put("lastUpdateTime", safe(note.getLastUpdateTime()));
        source.put("collectCount", safeInt(note.getCollectCount()));
        source.put("likeCount", safeInt(note.getLikeCount()));
        source.put("shareCount", safeInt(note.getShareCount()));
        source.put("commentCount", safeInt(note.getCommentCount()));
        source.put("mediaUrls", parseJsonArray(note.getImageUrlsJson()));
        return source;
    }

    private String buildSourceText(Note note) {
        return "【数据获取时间】" + safe(note.getFetchedAt()) + "\n"
                + "【笔记标题】" + safe(note.getTitle()) + "\n"
                + "【笔记内容】\n" + safe(note.getOriginalContent()) + "\n"
                + "【笔记话题】" + String.join(" ", parseTags(note.getTagsJson())) + "\n"
                + "【博主名字】" + safe(note.getAuthorName()) + "\n"
                + "【博主签名】" + blankAsNone(note.getAuthorSignature()) + "\n"
                + "【笔记发布时间】" + safe(note.getPublishTime()) + "\n"
                + "【最后更新时间】" + safe(note.getLastUpdateTime()) + "\n"
                + "【收藏量】" + safeInt(note.getCollectCount()) + "\n"
                + "【点赞量】" + safeInt(note.getLikeCount()) + "\n"
                + "【转发量】" + safeInt(note.getShareCount()) + "\n"
                + "【评论量】" + safeInt(note.getCommentCount()) + "\n"
                + "【视频配图或截图】" + String.join(", ", parseJsonArray(note.getImageUrlsJson()));
    }

    private List<String> parseTags(String json) {
        List<String> values = parseJsonArray(json);
        List<String> tags = new ArrayList<>();
        for (String value : values) {
            if (value.startsWith("#")) {
                tags.add(value);
            } else if (StringUtils.hasText(value)) {
                tags.add("#" + value);
            }
        }
        return tags;
    }

    private List<String> parseJsonArray(String json) {
        List<String> values = new ArrayList<>();
        if (!StringUtils.hasText(json)) {
            return values;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isArray()) {
                for (JsonNode item : node) {
                    values.add(item.asText());
                }
            }
        } catch (Exception ignored) {
            // Keep display resilient when old records contain malformed JSON strings.
        }
        return values;
    }

    private int scoreTitle(String title) {
        int score = 50;
        String safeTitle = safe(title);
        if (safeTitle.length() >= 12 && safeTitle.length() <= 30) score += 15;
        if (safeTitle.matches(".*\\d+.*")) score += 10;
        if (safeTitle.contains("!") || safeTitle.contains("？") || safeTitle.contains("?")) score += 8;
        if (safeTitle.contains("亲测") || safeTitle.contains("避坑") || safeTitle.contains("收藏")) score += 12;
        return Math.min(100, score);
    }

    private String blankAsNone(String value) {
        return StringUtils.hasText(value) ? value : "无";
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("JSON 序列化失败", ex);
        }
    }
}
