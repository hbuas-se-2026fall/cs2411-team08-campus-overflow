package com.campusoverflow.qa.infrastructure.persistence;

import com.campusoverflow.qa.domain.EndorsementLog;
import com.campusoverflow.qa.domain.EndorsementLogRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class EndorsementLogRepositoryAdapter implements EndorsementLogRepository {

    private final EndorsementLogJpaRepository jpa;

    public EndorsementLogRepositoryAdapter(EndorsementLogJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(EndorsementLog log) {
        EndorsementLogJpaEntity saved = jpa.save(new EndorsementLogJpaEntity(log.answerId(), log.endorserId(),
                log.answerVersion(), log.action().name(), log.reason(), log.createdAt()));
        log.assignId(saved.getId());
    }

    @Override
    public List<EndorsementLog> findByAnswer(long answerId) {
        return jpa.findByAnswerIdOrderByIdAsc(answerId).stream()
                .map(e -> EndorsementLog.reconstitute(e.getId(), e.getAnswerId(), e.getEndorserId(),
                        e.getAnswerVersion(), EndorsementLog.Action.valueOf(e.getAction()), e.getReason(),
                        e.getCreatedAt()))
                .toList();
    }
}
