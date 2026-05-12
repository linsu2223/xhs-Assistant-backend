package com.xhs.rewriter.web.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RewriteRequest {
    private String mode = "仿写模式";
    private String style = "小红书爆款风";
    private String keywords;
    private String lengthRange = "300-500字";
    private Integer versionCount = 1;
}
