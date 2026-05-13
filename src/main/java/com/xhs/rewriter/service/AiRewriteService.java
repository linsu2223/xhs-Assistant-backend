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
        data.put("titleRewrite", runTask(note, request.getTitlePrompt(), fallbackTitleRewrite(note)));
        data.put("contentRewrite", runTask(note, request.getContentPrompt(), fallbackContentRewrite(note)));
        data.put("sentimentAnalysis", runTask(note, request.getSentimentPrompt(), fallbackSentimentAnalysis(note)));
        data.put("copyAnalysis", runTask(note, request.getCopyPrompt(), fallbackCopyAnalysis(note)));
        return toJson(data);
    }

    private String runTask(Note note, String prompt, String fallback) {
        if (!StringUtils.hasText(deepSeekApiKey) || !StringUtils.hasText(prompt)) {
            return fallback;
        }
        try {
            return callDeepSeek(prompt, note);
        } catch (RuntimeException ex) {
            return fallback + "\n\n（DeepSeek 调用失败，当前展示本地格式化结果。）";
        }
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

    private String fallbackTitleRewrite(Note note) {
        String keyword = firstKeyword(note);
        return "1. " + keyword + "救星✨💄\n"
                + "2. 通勤不脱妆🌿✨\n"
                + "3. 底妆稳一天💫🪞";
    }

    private String fallbackContentRewrite(Note note) {
        return "早八通勤也想拥有干净底妆？这套方法我真的会反复用✨\n\n"
                + "我这次重点调整了三个步骤：妆前先控油、粉底薄涂、鼻翼和下巴少量多次定妆。看起来很简单，但对混油皮真的很友好。\n\n"
                + "🔍重点总结\n"
                + "1. 妆前别贪多，控油区域精准一点。\n"
                + "2. 粉底薄涂更自然，后续也不容易斑驳。\n"
                + "3. 中午用纸巾按压，比反复补粉更干净。\n\n"
                + "你们通勤最怕底妆哪里先崩？鼻翼、下巴还是额头？\n\n"
                + "#通勤妆 #底妆 #混油皮 #不脱妆 #小红书美妆";
    }

    private String fallbackSentimentAnalysis(Note note) {
        return "【主要情感】：积极\n"
                + "【情感强度】：4分\n"
                + "【情感关键词】：\n"
                + "- 适合：积极\n"
                + "- 完整：积极\n"
                + "- 清爽：积极\n"
                + "【情感句段分析】：\n"
                + "1. \"" + firstSentence(note) + "\"：表达了对使用效果的认可，整体偏正向。\n"
                + "2. \"重点是妆前控油、薄涂粉底、局部定妆\"：呈现解决问题的方法，带有实用和确定感。\n"
                + "3. \"可以先从这套步骤试试看\"：语气温和，传递分享和建议的情绪。\n"
                + "【情感变化】：从问题场景进入解决方案，最后转为经验分享。\n"
                + "【整体评估】：\n"
                + "- 情感基调：真实、轻松、偏积极。\n"
                + "- 心理状态推测：作者希望帮助同类肤质用户降低试错成本。\n"
                + "- 建议（如适用）：可增加更具体的前后对比，增强可信度。\n"
                + "【情感词云】：适合、完整、清爽、真实、友好、有效";
    }

    private String fallbackCopyAnalysis(Note note) {
        int length = safe(note.getOriginalContent()).length();
        return "# 小红书文案分析 ✨\n"
                + "## 📌 内容评估\n"
                + "这篇内容聚焦通勤底妆场景，主题明确，目标受众是关注持妆、控油和自然妆感的年轻女性，实用价值较强。\n"
                + "## 🔍 表达风格\n"
                + "语言偏真实分享，口语化程度较高，能建立亲近感，但情绪记忆点和互动表达还可以继续加强。\n"
                + "## 📊 结构分析\n"
                + "- 标题：⭐⭐⭐⭐ [主题清楚，但爆点略弱]\n"
                + "- 段落：结构较顺，建议增加步骤编号。\n"
                + "- 排版：可增加 emoji 和小标题提升扫读效率。\n"
                + "- 字数：约" + length + "字\n"
                + "## 🔄 互动潜力\n"
                + "内容具备收藏价值，若增加评论提问和对比细节，传播潜力会更强。\n"
                + "## ✅ 优点\n"
                + "1. 场景具体，用户代入感强。\n"
                + "2. 方法可执行，适合收藏。\n"
                + "3. 表达真实，不夸张。\n"
                + "## 🚀 建议\n"
                + "1. 标题加入痛点或结果承诺。\n"
                + "2. 结尾增加互动问题。\n"
                + "## 💯 总评：82/100\n"
                + "整体是适合继续打磨成小红书实用型内容的优质素材。";
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

    private String firstKeyword(Note note) {
        List<String> tags = parseTags(note.getTagsJson());
        if (!tags.isEmpty()) {
            return tags.get(0).replace("#", "");
        }
        String title = safe(note.getTitle());
        return title.length() > 4 ? title.substring(0, 4) : "笔记";
    }

    private String firstSentence(Note note) {
        String content = safe(note.getOriginalContent()).trim();
        if (content.isEmpty()) {
            return safe(note.getTitle());
        }
        String[] parts = content.split("[。！？!?\\n]+");
        return parts.length == 0 ? content : parts[0];
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
