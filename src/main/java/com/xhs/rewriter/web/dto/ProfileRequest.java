package com.xhs.rewriter.web.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileRequest {
    private String displayName;
    private String phone;
    private String email;
    private String avatarUrl;
}
