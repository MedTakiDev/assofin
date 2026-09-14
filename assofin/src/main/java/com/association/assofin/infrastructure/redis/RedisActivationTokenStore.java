package com.association.assofin.infrastructure.redis;

import com.association.assofin.auth.activation.ActivationTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RedisActivationTokenStore implements ActivationTokenStore {

    private static final String KEY_PREFIX = "auth:activation:token:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(String tokenHash,Long userId,Duration ttl) {
        String key = buildKey(tokenHash);
        redisTemplate.opsForValue().set(key,userId.toString(),ttl);
    }

    @Override
    public Optional<Long> findUserId(String tokenHash) {
        String key = buildKey(tokenHash);
        String value = redisTemplate
                .opsForValue()
                .get(key);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(value));
    }

    @Override
    public void delete(String tokenHash) {
        redisTemplate.delete(buildKey(tokenHash));
    }

    private String buildKey(String tokenHash) {
        return KEY_PREFIX + tokenHash;
    }
}