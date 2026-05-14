package com.xhs.rewriter.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Note {
    private Long id;
    private String title;
    private String noteUrl;
    private String originalContent;
    private String imageUrlsJson;
    private Integer likeCount = 0;
    private Integer collectCount = 0;
    private Integer commentCount = 0;
    private Integer shareCount = 0;
    private Integer interactionScore = 0;
    private String authorName;
    private String authorSignature = "无";
    private String publishTime;
    private String lastUpdateTime;
    private String fetchedAt;
    private String tagsJson;
    private String analysisJson;
    private String rewriteResultsJson;
    private String potentialLabel = "待分析";
    private String remark;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
}
