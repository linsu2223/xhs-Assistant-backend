package com.xhs.rewriter.web.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NoteRequest {
    private String title;
    private String originalContent;
    private String noteUrl;
    private String remark;
}
