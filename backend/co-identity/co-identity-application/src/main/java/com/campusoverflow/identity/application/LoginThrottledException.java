package com.campusoverflow.identity.application;

import com.campusoverflow.shared.domain.DomainException;

/** 登录限流锁定。携带剩余等待秒数，前端据此展示可读的等待信息（F02）。 */
public class LoginThrottledException extends DomainException {

    private final long retryAfterSeconds;

    public LoginThrottledException(long retryAfterSeconds) {
        super("ID-429-THROTTLED", "登录失败次数过多，请 " + Math.max(retryAfterSeconds, 1) + " 秒后再试");
        this.retryAfterSeconds = Math.max(retryAfterSeconds, 1);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public int suggestedStatus() {
        return 429;
    }
}
