package com.xhs.rewriter.web;

import com.xhs.rewriter.domain.UserAccount;
import com.xhs.rewriter.mapper.UserAccountMapper;
import com.xhs.rewriter.security.JwtService;
import com.xhs.rewriter.web.dto.LoginRequest;
import com.xhs.rewriter.web.dto.LoginResponse;
import com.xhs.rewriter.web.dto.RegisterRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,20}$");
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserAccountMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService, UserAccountMapper userMapper, PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        UserAccount account = userMapper.findByUsername(request.getUsername());
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }
        if (account.getLockedUntil() != null && account.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.LOCKED, "连续登录失败，账号已锁定15分钟");
        }
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
            account.setFailedLoginCount(0);
            account.setLockedUntil(null);
            userMapper.update(account);
            return new LoginResponse(jwtService.createToken(request.getUsername()), request.getUsername());
        } catch (RuntimeException ex) {
            int failed = account.getFailedLoginCount() == null ? 1 : account.getFailedLoginCount() + 1;
            account.setFailedLoginCount(failed);
            if (failed >= 5) {
                account.setLockedUntil(LocalDateTime.now().plusMinutes(15));
            }
            userMapper.update(account);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }
    }

    @PostMapping("/register")
    public LoginResponse register(@RequestBody RegisterRequest request) {
        if (request.getAccount() == null || request.getAccount().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "账号不能为空");
        }
        if (!PASSWORD_PATTERN.matcher(request.getPassword() == null ? "" : request.getPassword()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码需8-20位，含大小写字母和数字");
        }
        String account = request.getAccount().trim();
        if (userMapper.countByUsername(account) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "账号已存在");
        }
        UserAccount user = new UserAccount();
        user.setUsername(account);
        user.setDisplayName(account);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        if (account.contains("@")) {
            user.setEmail(account);
        } else {
            user.setPhone(account);
        }
        userMapper.insert(user);
        return new LoginResponse(jwtService.createToken(account), account);
    }

    @PostMapping("/send-code")
    public Map<String, String> sendCode(@RequestBody RegisterRequest request) {
        Map<String, String> data = new HashMap<>();
        data.put("message", "验证码已发送，演示环境默认填写 123456");
        return data;
    }
}
