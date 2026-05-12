package com.xhs.rewriter.web.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {
    private String account;
    private String password;
    private String code;
}
