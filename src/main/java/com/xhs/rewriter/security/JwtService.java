package com.xhs.rewriter.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;

@Service
public class JwtService {
    private final Key key;
    private final long expireHours;

    public JwtService(@Value("${app.jwt-secret}") String secret, @Value("${app.jwt-expire-hours}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(normalizeKey(secret));
        this.expireHours = expireHours;
    }

    public String createToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(expireHours, ChronoUnit.HOURS)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String parseUsername(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();
    }

    private byte[] normalizeKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Arrays.copyOf(digest.digest(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)), 32);
        } catch (Exception ex) {
            throw new IllegalStateException("JWT 密钥初始化失败", ex);
        }
    }
}
