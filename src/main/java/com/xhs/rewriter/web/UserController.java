package com.xhs.rewriter.web;

import com.xhs.rewriter.domain.UserAccount;
import com.xhs.rewriter.repository.UserAccountRepository;
import com.xhs.rewriter.service.CookieCryptoService;
import com.xhs.rewriter.web.dto.CookieRequest;
import com.xhs.rewriter.web.dto.ProfileRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserAccountRepository userRepository;
    private final CookieCryptoService cookieCryptoService;

    public UserController(UserAccountRepository userRepository, CookieCryptoService cookieCryptoService) {
        this.userRepository = userRepository;
        this.cookieCryptoService = cookieCryptoService;
    }

    @GetMapping("/profile")
    public Map<String, Object> profile(Authentication authentication) {
        UserAccount user = currentUser(authentication);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", user.getUsername());
        data.put("displayName", user.getDisplayName());
        data.put("phone", user.getPhone());
        data.put("email", user.getEmail());
        data.put("avatarUrl", user.getAvatarUrl());
        data.put("cookieStatus", user.getCookieStatus());
        data.put("cookieUpdatedAt", user.getCookieUpdatedAt());
        data.put("todayUsage", user.getTodayUsage());
        data.put("monthUsage", user.getMonthUsage());
        return data;
    }

    @PutMapping("/profile")
    public Map<String, Object> updateProfile(@RequestBody ProfileRequest request, Authentication authentication) {
        UserAccount user = currentUser(authentication);
        user.setDisplayName(request.getDisplayName());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setAvatarUrl(request.getAvatarUrl());
        userRepository.save(user);
        return profile(authentication);
    }

    @PostMapping("/cookie")
    public Map<String, String> saveCookie(@RequestBody CookieRequest request, Authentication authentication) {
        if (request.getCookie() == null || request.getCookie().trim().length() < 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cookie格式不正确");
        }
        UserAccount user = currentUser(authentication);
        user.setEncryptedCookie(cookieCryptoService.encrypt(request.getCookie().trim()));
        user.setCookieStatus("有效");
        user.setCookieUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        Map<String, String> data = new LinkedHashMap<>();
        data.put("status", "有效");
        data.put("masked", cookieCryptoService.mask(request.getCookie().trim()));
        return data;
    }

    @PostMapping("/cookie/test")
    public Map<String, String> testCookie(@RequestBody CookieRequest request) {
        if (request.getCookie() == null || request.getCookie().trim().length() < 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cookie已过期，请重新配置");
        }
        Map<String, String> data = new LinkedHashMap<>();
        data.put("status", "有效");
        data.put("message", "Cookie格式校验通过，真实有效性需接入小红书请求后验证");
        return data;
    }

    @DeleteMapping("/cookie")
    public void deleteCookie(Authentication authentication) {
        UserAccount user = currentUser(authentication);
        user.setEncryptedCookie(null);
        user.setCookieStatus("未配置");
        user.setCookieUpdatedAt(null);
        userRepository.save(user);
    }

    private UserAccount currentUser(Authentication authentication) {
        String username = authentication == null ? "" : authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录"));
    }
}
