package com.xhs.rewriter.domain;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 64)
    private String displayName;

    @Column(length = 64)
    private String phone;

    @Column(length = 128)
    private String email;

    @Column(length = 512)
    private String avatarUrl;

    @Lob
    private String encryptedCookie;

    @Column(length = 32)
    private String cookieStatus = "未配置";

    private LocalDateTime cookieUpdatedAt;

    private Integer todayUsage = 0;

    private Integer monthUsage = 0;

    private Integer failedLoginCount = 0;

    private LocalDateTime lockedUntil;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
