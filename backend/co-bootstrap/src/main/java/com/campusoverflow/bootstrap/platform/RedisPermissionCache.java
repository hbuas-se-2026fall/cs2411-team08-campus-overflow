package com.campusoverflow.bootstrap.platform;

import com.campusoverflow.shared.security.PermissionCache;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link PermissionCache} 的 Redis 实现：版本号失效 + TTL 兜底。
 * <ul>
 *   <li>版本键 {@code co:perm:v:{owner}}：不存在时视为 1 并原子初始化；</li>
 *   <li>数据键 {@code co:perm:{owner}:v{版本}:{name}}：版本递增后旧键自然不可达，免逐键删除；</li>
 *   <li>TTL 兜底：即使失效事件丢失（Redis 主从切换等），最多 TTL 后缓存收敛。</li>
 * </ul>
 */
@Component
public class RedisPermissionCache implements PermissionCache {

    private static final String VERSION_KEY_PREFIX = "co:perm:v:";
    private static final String DATA_KEY_FORMAT = "co:perm:%s:v%d:%s";
    private static final String INITIAL_VERSION = "1";

    private final StringRedisTemplate redis;

    public RedisPermissionCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String load(String ownerKey, String cacheName, Duration ttl, Supplier<String> loader) {
        long version = currentVersion(ownerKey);
        String dataKey = DATA_KEY_FORMAT.formatted(ownerKey, version, cacheName);
        String cached = redis.opsForValue().get(dataKey);
        if (cached != null) {
            return cached;
        }
        String fresh = loader.get();
        // 回源期间版本可能已被 bump，写入旧版本键只是多占一个将过期键，不影响正确性
        redis.opsForValue().set(dataKey, fresh, ttl);
        return fresh;
    }

    @Override
    public void evictAll(String ownerKey) {
        redis.opsForValue().increment(VERSION_KEY_PREFIX + ownerKey);
    }

    private long currentVersion(String ownerKey) {
        String version = redis.opsForValue().get(VERSION_KEY_PREFIX + ownerKey);
        if (version == null) {
            Boolean first = redis.opsForValue().setIfAbsent(VERSION_KEY_PREFIX + ownerKey, INITIAL_VERSION);
            return first != null && first ? 1 : currentVersionAfterRace(ownerKey);
        }
        return parse(version);
    }

    private long currentVersionAfterRace(String ownerKey) {
        String version = redis.opsForValue().get(VERSION_KEY_PREFIX + ownerKey);
        return version == null ? 1 : parse(version);
    }

    private static long parse(String version) {
        try {
            return Long.parseLong(version);
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
