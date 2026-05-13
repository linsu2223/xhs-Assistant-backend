package com.xhs.rewriter.domain;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
public class Note {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 512)
    private String noteUrl;

    @Lob
    @Column(nullable = false)
    private String originalContent;

    @Lob
    private String imageUrlsJson;

    private Integer likeCount = 0;

    private Integer collectCount = 0;

    private Integer commentCount = 0;

    private Integer shareCount = 0;

    private Integer interactionScore = 0;

    @Column(length = 64)
    private String authorName;

    @Column(length = 512)
    private String authorSignature = "无";

    @Column(length = 128)
    private String publishTime;

    @Column(length = 128)
    private String lastUpdateTime;

    @Column(length = 128)
    private String fetchedAt;

    @Lob
    private String tagsJson;

    @Lob
    private String analysisJson;

    @Lob
    private String rewriteResultsJson;

    @Column(length = 32)
    private String potentialLabel = "待分析";

    @Column(length = 255)
    private String remark;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
