package com.fraudshield.service;

import com.fraudshield.dto.LoginResponse;
import com.fraudshield.dto.UserDto;
import com.fraudshield.model.AppUser;
import com.fraudshield.model.RefreshToken;
import com.fraudshield.repository.AppUserRepository;
import com.fraudshield.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AuthService {

    private static final long EXPIRES_IN_SECONDS = 900;

    private final AppUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final long refreshTtlMs;

    public AuthService(AppUserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuditService auditService,
                       @Value("${jwt.refresh-ttl-ms}") long refreshTtlMs) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.refreshTtlMs = refreshTtlMs;
    }

    @Transactional
    public LoginResponse login(String email, String password, String ip) {
        AppUser user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        auditService.log(user.getName(), "Logged in", ip, "\uD83D\uDD11");
        return issueTokens(user);
    }

    @Transactional
    public LoginResponse refresh(String rawRefreshToken) {
        String hash = jwtService.sha256Hex(rawRefreshToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        if (existing.isUsed() || existing.getExpiresAt().isBefore(Instant.now())) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        existing.setUsed(true);
        refreshTokenRepository.save(existing);

        AppUser user = userRepository.findById(existing.getUserId())
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        return issueTokens(user);
    }

    @Transactional
    public void logout(String email) {
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            List<RefreshToken> active = refreshTokenRepository.findByUserIdAndUsedFalse(user.getId());
            active.forEach(t -> t.setUsed(true));
            refreshTokenRepository.saveAll(active);
            auditService.log(user.getName(), "Logged out", "-", "\uD83D\uDEAA");
        });
    }

    public UserDto me(String email) {
        AppUser user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid session"));
        return toUserDto(user);
    }

    private LoginResponse issueTokens(AppUser user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefresh = jwtService.generateRawRefreshToken();
        RefreshToken refreshToken = new RefreshToken(
                jwtService.sha256Hex(rawRefresh),
                user.getId(),
                Instant.now().plusMillis(refreshTtlMs),
                false
        );
        refreshTokenRepository.save(refreshToken);
        return new LoginResponse(accessToken, rawRefresh, EXPIRES_IN_SECONDS, toUserDto(user));
    }

    private UserDto toUserDto(AppUser user) {
        return new UserDto(user.getName(), user.getEmail(), user.getRole().name());
    }
}
