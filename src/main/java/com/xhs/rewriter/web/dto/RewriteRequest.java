package com.xhs.rewriter.web.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RewriteRequest {
    private String titlePrompt;
    private String contentPrompt;
    private String sentimentPrompt;
    private String copyPrompt;
}
