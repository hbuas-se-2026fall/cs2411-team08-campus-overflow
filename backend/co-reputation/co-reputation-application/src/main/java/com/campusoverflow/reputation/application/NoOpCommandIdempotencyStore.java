package com.campusoverflow.reputation.application;

import com.campusoverflow.shared.command.CommandIdempotencyStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** 兼容既有单元测试的幂等存储：不做占位，行为等同于「未启用命令幂等」。 */
final class NoOpCommandIdempotencyStore implements CommandIdempotencyStore {

    static final NoOpCommandIdempotencyStore INSTANCE = new NoOpCommandIdempotencyStore();

    private NoOpCommandIdempotencyStore() {
    }

    @Override
    public BeginOutcome begin(String key, Duration ttl, Instant now) {
        return BeginOutcome.EXECUTE;
    }

    @Override
    public void complete(String key, String resultJson, Instant now) {
    }

    @Override
    public Optional<String> resultOf(String key) {
        return Optional.empty();
    }
}
