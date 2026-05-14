package com.xhs.rewriter.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class UserAccount {
    private Long id;
    private String username;
    private String password;
    private String displayName;
    private String phone;
    private String email;
    private String avatarUrl;
    private String encryptedCookie;
    private String cookieStatus = "未配置";
    private LocalDateTime cookieUpdatedAt;
    private Integer todayUsage = 0;
    private Integer monthUsage = 0;
    private Integer failedLoginCount = 0;
    private LocalDateTime lockedUntil;
    private LocalDateTime createdAt = LocalDateTime.now();
}
