package com.xhs.rewriter.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

@Service
public class CookieCryptoService {
    private final SecretKeySpec keySpec;

    public CookieCryptoService(@Value("${app.cookie-secret}") String secret) {
        this.keySpec = new SecretKeySpec(normalizeKey(secret), "AES");
    }

    public String encrypt(String raw) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            return Base64.getEncoder().encodeToString(cipher.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Cookie 加密失败", ex);
        }
    }

    public String decrypt(String encrypted) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return new String(cipher.doFinal(Base64.getDecoder().decode(encrypted)), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Cookie 解密失败", ex);
        }
    }

    public String mask(String cookie) {
        if (cookie == null || cookie.length() <= 10) {
            return "***";
        }
        return cookie.substring(0, 10) + "***";
    }

    private byte[] normalizeKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Arrays.copyOf(digest.digest(secret.getBytes(StandardCharsets.UTF_8)), 32);
        } catch (Exception ex) {
            throw new IllegalStateException("Cookie 密钥初始化失败", ex);
        }
    }
}
