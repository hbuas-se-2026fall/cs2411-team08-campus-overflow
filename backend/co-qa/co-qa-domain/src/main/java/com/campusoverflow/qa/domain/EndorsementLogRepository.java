package com.campusoverflow.qa.domain;

import java.util.List;

/** 认证历史的持久化端口（只追加账本，不提供更新与删除）。 */
public interface EndorsementLogRepository {

    void save(EndorsementLog log);

    List<EndorsementLog> findByAnswer(long answerId);
}
