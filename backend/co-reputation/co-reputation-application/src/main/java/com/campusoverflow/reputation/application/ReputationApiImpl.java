package com.campusoverflow.reputation.application;

import com.campusoverflow.reputation.api.ReputationApi;
import com.campusoverflow.reputation.api.ReputationSummary;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReputationApiImpl implements ReputationApi {

    private final ReputationAccountRepository accounts;

    public ReputationApiImpl(ReputationAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public ReputationSummary summary(long userId) {
        int reputation = accounts.findByUserId(userId).map(ReputationAccount::reputation).orElse(0);
        return new ReputationSummary(userId, reputation);
    }

    @Override
    public Map<Long, Integer> reputations(Collection<Long> userIds) {
        return accounts.findAllByUserId(userIds).stream()
                .collect(Collectors.toMap(ReputationAccount::userId, ReputationAccount::reputation));
    }
}
