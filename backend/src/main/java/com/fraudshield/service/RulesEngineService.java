package com.fraudshield.service;

import com.fraudshield.model.FraudRule;
import com.fraudshield.model.Transaction;
import com.fraudshield.model.TransactionStatus;
import com.fraudshield.model.Verdict;
import com.fraudshield.repository.FraudRuleRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class RulesEngineService {

    private static final BigDecimal AMOUNT_THRESHOLD = new BigDecimal("2000");
    private static final BigDecimal NIGHTOWL_AMOUNT_THRESHOLD = new BigDecimal("1000");

    private final FraudRuleRepository fraudRuleRepository;

    public RulesEngineService(FraudRuleRepository fraudRuleRepository) {
        this.fraudRuleRepository = fraudRuleRepository;
    }

    public ScoreResult score(Transaction candidate) {
        List<FraudRule> enabledRules = fraudRuleRepository.findByEnabledTrue();
        int score = 0;
        List<String> firedRuleIds = new ArrayList<>();

        for (FraudRule rule : enabledRules) {
            if (fires(rule, candidate)) {
                score += rule.getWeight();
                firedRuleIds.add(rule.getId());
            }
        }

        Verdict verdict;
        TransactionStatus status;
        if (score >= 50) {
            verdict = Verdict.FLAGGED;
            status = TransactionStatus.PENDING;
        } else {
            verdict = Verdict.PASSED;
            status = TransactionStatus.PASSED;
        }

        return new ScoreResult(score, verdict, status, firedRuleIds);
    }

    /**
     * Deterministic firing heuristics (demo-grade):
     * - amount: fires when amount > 2000
     * - nightowl: fires when amount > 1000 AND createdAt hour is in [2,5) (UTC)
     * - velocity/geo/duplicate: deterministic pseudo-check on a stable hash of (id + merchantId)
     */
    private boolean fires(FraudRule rule, Transaction tx) {
        BigDecimal amount = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
        switch (rule.getId()) {
            case "amount":
                return amount.compareTo(AMOUNT_THRESHOLD) > 0;
            case "nightowl": {
                if (amount.compareTo(NIGHTOWL_AMOUNT_THRESHOLD) <= 0 || tx.getCreatedAt() == null) {
                    return false;
                }
                int hour = tx.getCreatedAt().atZone(ZoneOffset.UTC).getHour();
                return hour >= 2 && hour < 5;
            }
            case "velocity":
                return bucket(tx, 7) < 25;
            case "geo":
                return bucket(tx, 13) < 22;
            case "duplicate":
                return bucket(tx, 19) < 20;
            default:
                return false;
        }
    }

    private int bucket(Transaction tx, int salt) {
        String seed = (tx.getId() != null ? tx.getId() : "") + "|"
                + (tx.getMerchantId() != null ? tx.getMerchantId() : "") + "|" + salt;
        return Math.floorMod(seed.hashCode(), 100);
    }

    public record ScoreResult(int score, Verdict verdict, TransactionStatus status, List<String> firedRuleIds) {
    }
}
