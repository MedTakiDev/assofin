package com.association.assofin.auth.activation;


import java.time.Duration;
import java.util.Optional;

public interface ActivationTokenStore {

    void save(
            String tokenHash,
            Long userId,
            Duration ttl
    );

    Optional<Long> findUserId(
            String tokenHash
    );

    void delete(
            String tokenHash
    );
}