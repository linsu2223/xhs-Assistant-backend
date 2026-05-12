package com.xhs.rewriter.web;

import com.xhs.rewriter.service.FeishuImportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/feishu")
public class FeishuController {
    private final FeishuImportService feishuImportService;

    public FeishuController(FeishuImportService feishuImportService) {
        this.feishuImportService = feishuImportService;
    }

    @GetMapping("/status")
    public Map<String, String> status() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("baseUrl", feishuImportService.getDefaultBaseUrl());
        data.put("message", feishuImportService.explainNextStep());
        return data;
    }
}
