package com.association.assofin.infrastructure.redis.components;


import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RedisDebugConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisDebugConfig.class);

    @Value("${spring.data.redis.password:NOT_SET}")
    private String redisPassword;

    @Value("${spring.data.redis.host:NOT_SET}")
    private String redisHost;

    @Value("${spring.data.redis.port:NOT_SET}")
    private String redisPort;

    @PostConstruct
    public void printConfig() {
        log.info("== REDIS DEBUG ==");
        log.info("Redis Host: {}", redisHost);
        log.info("Redis Port: {}", redisPort);
        log.info("Redis Password Loaded Length: {}", redisPassword.equals("NOT_SET") ? "NOT_SET" : redisPassword.length());
        log.info("==================");
    }
}