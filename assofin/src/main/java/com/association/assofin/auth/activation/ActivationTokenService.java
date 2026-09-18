package com.association.assofin.auth.activation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ActivationTokenService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);

    private final ActivationTokenStore activationTokenStore;

    private final SecureRandom secureRandom = new SecureRandom();

    public String createToken(Long userId) {
        String rawToken = generateToken();
        String tokenHash = hashToken(rawToken);
        activationTokenStore.save(
                tokenHash,
                userId,
                TOKEN_TTL
        );
        return rawToken;
    }

    public Long validateToken(String rawToken) {

        String tokenHash = hashToken(rawToken);

        return activationTokenStore
                .findUserId(tokenHash)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Invalid or expired activation token"
                        )
                );
    }

    public Long consumeToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        Optional<Long> userId =
                activationTokenStore.findUserId(tokenHash);
        if (userId.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid or expired activation token"
            );
        }
        activationTokenStore.delete(tokenHash);
        return userId.get();
    }

    public void invalidateToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        activationTokenStore.delete(tokenHash);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");
            byte[] hash =digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available",exception);
        }
    }
}