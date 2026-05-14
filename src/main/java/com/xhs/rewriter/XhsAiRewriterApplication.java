package com.xhs.rewriter;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.xhs.rewriter.mapper")
public class XhsAiRewriterApplication {
    public static void main(String[] args) {
        SpringApplication.run(XhsAiRewriterApplication.class, args);
    }
}
