package com.campusoverflow.shared.security;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 权限缓存端口（B01）：课程角色、成员资格等授权数据的缓存读取与失效。
 *
 * <p>失效策略采用<b>版本号</b>：缓存键携带用户级版本号，角色调整/成员移除/登出时版本 +1，
 * 旧键立即不可见，无需逐键删除；所有键同时带 TTL 作为兜底，失效事件丢失时最多 TTL 时长后收敛。
 * 实现方负责保证 {@link #evictAll} 之后 {@link #load} 一定回源。
 */
public interface PermissionCache {

    /**
     * 读取缓存值；未命中时调用 loader 回源并写缓存。
     *
     * @param ownerKey  缓存属主（如用户 ID 的字符串形式）
     * @param cacheName 缓存名称（如 courseRoles），同一属主可有多个缓存
     * @param ttl       兜底过期时间，建议不超过 5 分钟
     * @param loader    回源函数，返回 JSON 字符串（由调用方负责序列化/反序列化）
     * @return 缓存或回源得到的 JSON 值
     */
    String load(String ownerKey, String cacheName, Duration ttl, Supplier<String> loader);

    /** 使该属主的全部权限缓存立即失效（版本号 +1）。 */
    void evictAll(String ownerKey);
}
