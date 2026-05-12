package com.xhs.rewriter.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FeishuImportService {
    private final String defaultBaseUrl;

    public FeishuImportService(@Value("${app.feishu.base-url}") String defaultBaseUrl) {
        this.defaultBaseUrl = defaultBaseUrl;
    }

    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }

    public String explainNextStep() {
        return "已保留飞书同步入口。接入时需要飞书应用 appId/appSecret、tenant_access_token、base token、table id 和 view id。";
    }
}
