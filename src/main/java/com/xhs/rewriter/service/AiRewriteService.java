package com.xhs.rewriter.service;

import com.xhs.rewriter.domain.Note;
import com.xhs.rewriter.web.dto.RewriteRequest;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AiRewriteService {
    public String analyze(Note note) {
        int titleScore = scoreTitle(note.getTitle());
        int contentScore = Math.min(95, 45 + safe(note.getOriginalContent()).length() / 8);
        int interactionScore = Math.min(100, (int) (note.getLikeCount() * 0.03 + note.getCollectCount() * 0.04 + note.getCommentCount() * 0.3));
        int tagScore = safe(note.getTagsJson()).contains("#") ? 82 : 45;
        int imageScore = safe(note.getImageUrlsJson()).length() > 5 ? 78 : 42;
        int total = (titleScore + contentScore + interactionScore + tagScore + imageScore) / 5;
        note.setInteractionScore(total);
        note.setPotentialLabel(total >= 80 ? "高潜力" : total >= 60 ? "中等" : "待优化");

        return "{"
                + "\"titleScore\":" + titleScore + ","
                + "\"contentScore\":" + contentScore + ","
                + "\"interactionScore\":" + interactionScore + ","
                + "\"tagScore\":" + tagScore + ","
                + "\"imageScore\":" + imageScore + ","
                + "\"totalScore\":" + total + ","
                + "\"potentialLabel\":\"" + note.getPotentialLabel() + "\","
                + "\"keywords\":[\"种草\",\"体验\",\"步骤\",\"收藏\"],"
                + "\"suggestions\":["
                + "\"标题可以加入数字或结果型表达，提升点击动机\","
                + "\"正文建议按场景、步骤、避坑、总结组织，增强收藏价值\","
                + "\"结尾加入评论问题，提升互动率\""
                + "]"
                + "}";
    }

    public String rewrite(Note note, RewriteRequest request) {
        int count = request.getVersionCount() == null ? 1 : Math.max(1, Math.min(3, request.getVersionCount()));
        StringBuilder json = new StringBuilder("[");
        for (int i = 1; i <= count; i++) {
            if (i > 1) {
                json.append(",");
            }
            String title = buildTitle(note.getTitle(), request.getStyle(), i);
            String content = buildContent(note.getTitle(), note.getOriginalContent(), request, i);
            int similarity = Math.max(42, 78 - i * 8 - modeOffset(request.getMode()));
            json.append("{")
                    .append("\"version\":").append(i).append(",")
                    .append("\"title\":\"").append(escape(title)).append("\",")
                    .append("\"content\":\"").append(escape(content)).append("\",")
                    .append("\"similarity\":").append(similarity).append(",")
                    .append("\"description\":\"本版采用").append(escape(request.getMode())).append("，风格为").append(escape(request.getStyle())).append("\"")
                    .append("}");
        }
        json.append("]");
        return json.toString();
    }

    private String buildContent(String title, String content, RewriteRequest request, int index) {
        String cleaned = safe(content).trim();
        List<String> sentences = Arrays.stream(cleaned.split("[。！？!?\\n]+"))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .limit(4)
                .collect(Collectors.toList());
        String keywords = safe(request.getKeywords());
        String lead = "最近把「" + safe(title) + "」重新整理了一遍，发现真正有用的不是信息堆叠，而是把场景、方法和结果讲清楚。";
        if ("幽默吐槽风".equals(request.getStyle())) {
            lead = "本来只是想随手试试「" + safe(title) + "」，结果越看越觉得：这不整理出来真的说不过去。";
        } else if ("专业测评风".equals(request.getStyle())) {
            lead = "围绕「" + safe(title) + "」，我按使用场景、核心卖点和可复用程度做了一次结构化整理。";
        }
        String body = sentences.isEmpty() ? cleaned : String.join("。\n", sentences);
        return lead + "\n\n"
                + "核心结论：这类内容最适合用“问题引入 + 关键步骤 + 真实反馈”的方式表达。\n\n"
                + body + "。\n\n"
                + (keywords.isEmpty() ? "" : "这版会重点强化：" + keywords + "。\n\n")
                + "建议收藏后按这三个点检查：\n"
                + "1. 标题是否给出明确收益。\n"
                + "2. 正文是否有可照做的步骤。\n"
                + "3. 结尾是否留下评论互动入口。\n\n"
                + "你会更想看第 " + index + " 个版本继续扩成完整发布稿吗？";
    }

    private String buildTitle(String title, String style, int index) {
        String base = safe(title).trim().isEmpty() ? "这篇小红书笔记" : safe(title).trim();
        if (base.length() > 18) {
            base = base.substring(0, 18);
        }
        if ("极简干货风".equals(style)) {
            return base + "：3个关键点";
        }
        if ("走心故事风".equals(style)) {
            return "我为什么重新整理「" + base + "」";
        }
        return base + "｜亲测有效版" + index;
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

    private int modeOffset(String mode) {
        if ("仿写模式".equals(mode)) return 0;
        if ("扩写模式".equals(mode)) return 8;
        if ("缩写模式".equals(mode)) return 14;
        return 18;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String escape(String value) {
        return safe(value).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
