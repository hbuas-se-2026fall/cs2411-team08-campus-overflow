package com.campusoverflow.identity.application;

/** 登录防暴力破解端口（实现：Redis 计数器）。质量场景 QS-08。 */
public interface LoginThrottle {
    boolean isBlocked(String username);

    /** 锁定剩余秒数；未锁定返回 0。前端据此展示等待信息（F02）。 */
    long remainingBlockSeconds(String username);

    void recordFailure(String username);

    void reset(String username);
}
