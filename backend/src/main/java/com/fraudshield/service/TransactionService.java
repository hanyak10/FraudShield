package com.fraudshield.service;

import com.fraudshield.dto.CreateTransactionRequest;
import com.fraudshield.dto.CreateTransactionResponse;
import com.fraudshield.dto.TransactionResponse;
import com.fraudshield.dto.TriggeredRuleDto;
import com.fraudshield.exception.BadRequestException;
import com.fraudshield.exception.NotFoundException;
import com.fraudshield.model.FraudRule;
import com.fraudshield.model.Transaction;
import com.fraudshield.model.TransactionStatus;
import com.fraudshield.config.MockModeResolver;
import com.fraudshield.repository.FraudRuleRepository;
import com.fraudshield.repository.TransactionRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private static final long SEQ_BASE = 10000;

    private final TransactionRepository transactionRepository;
    private final FraudRuleRepository fraudRuleRepository;
    private final RulesEngineService rulesEngineService;
    private final AuditService auditService;
    private final MockModeResolver mockModeResolver;

    private final AtomicLong sequence = new AtomicLong(SEQ_BASE);

    public TransactionService(TransactionRepository transactionRepository,
                              FraudRuleRepository fraudRuleRepository,
                              RulesEngineService rulesEngineService,
                              AuditService auditService,
                              MockModeResolver mockModeResolver) {
        this.transactionRepository = transactionRepository;
        this.fraudRuleRepository = fraudRuleRepository;
        this.rulesEngineService = rulesEngineService;
        this.auditService = auditService;
        this.mockModeResolver = mockModeResolver;
    }

    /** Ensure the generated id sequence starts above any seeded ids. */
    public void primeSequence() {
        long max = transactionRepository.findAll().stream()
                .map(Transaction::getId)
                .filter(id -> id != null && id.startsWith("tx-"))
                .map(id -> {
                    try {
                        return Long.parseLong(id.substring(3));
                    } catch (NumberFormatException e) {
                        return SEQ_BASE;
                    }
                })
                .max(Long::compareTo)
                .orElse(SEQ_BASE - 1);
        sequence.set(Math.max(sequence.get(), max + 1));
    }

    @Transactional
    public CreateTransactionResponse create(CreateTransactionRequest request, String userId, String ip) {
        Transaction tx = new Transaction();
        tx.setId("tx-" + sequence.getAndIncrement());
        tx.setUserId(userId);
        tx.setAmount(request.amount());
        tx.setCurrency(request.currency().toUpperCase());
        tx.setMerchantId(request.merchantId());
        tx.setCountry(deriveCountry(request.latitude(), request.longitude()));
        tx.setCreatedAt(Instant.now());
        tx.setConfirmedFraud(false);
        tx.setSeeded(false);

        RulesEngineService.ScoreResult result = rulesEngineService.score(tx);
        tx.setScore(result.score());
        tx.setVerdict(result.verdict());
        tx.setStatus(result.status());
        tx.setTriggeredRules(String.join(",", result.firedRuleIds()));

        transactionRepository.save(tx);
        auditService.log("System", "Submitted transaction " + tx.getId(), ip, "\uD83D\uDCB3");

        return new CreateTransactionResponse(tx.getId(), tx.getStatus().name(), tx.getCreatedAt().toEpochMilli());
    }

    public List<TransactionResponse> list(String q, String verdict, String status) {
        Specification<Transaction> spec = buildSpec(q, verdict, status);
        List<Transaction> transactions = transactionRepository.findAll(spec);
        Map<String, FraudRule> ruleIndex = ruleIndex();
        return transactions.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(tx -> toResponse(tx, ruleIndex))
                .collect(Collectors.toList());
    }

    public TransactionResponse get(String id) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction not found: " + id));
        return toResponse(tx, ruleIndex());
    }

    @Transactional
    public TransactionResponse applyVerdict(String id, String decision, String actorName, String ip) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction not found: " + id));

        String action;
        String icon;
        if ("FALSE_POSITIVE".equals(decision)) {
            tx.setStatus(TransactionStatus.OVERRIDDEN);
            tx.setConfirmedFraud(false);
            action = "Overrode verdict on " + id + " \u2192 FALSE_POSITIVE";
            icon = "\uD83D\uDD01";
        } else if ("CONFIRMED_FRAUD".equals(decision)) {
            tx.setConfirmedFraud(true);
            action = "Confirmed fraud on " + id;
            icon = "\uD83D\uDEA8";
        } else {
            throw new BadRequestException("Invalid decision: " + decision);
        }

        transactionRepository.save(tx);
        auditService.log(actorName, action, ip, icon);
        return toResponse(tx, ruleIndex());
    }

    private Specification<Transaction> buildSpec(String q, String verdict, String status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (verdict != null && !verdict.isBlank()) {
                predicates.add(cb.equal(root.get("verdict"),
                        com.fraudshield.model.Verdict.valueOf(verdict.toUpperCase())));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"),
                        TransactionStatus.valueOf(status.toUpperCase())));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("id")), like),
                        cb.like(cb.lower(root.get("userId")), like),
                        cb.like(cb.lower(root.get("merchantId")), like)
                ));
            }
            // In real mode (mock off) hide seeded demo rows; in mock mode return everything.
            if (!mockModeResolver.isMockEnabled()) {
                predicates.add(cb.isFalse(root.get("seeded")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Map<String, FraudRule> ruleIndex() {
        return fraudRuleRepository.findAll().stream()
                .collect(Collectors.toMap(FraudRule::getId, r -> r));
    }

    private TransactionResponse toResponse(Transaction tx, Map<String, FraudRule> ruleIndex) {
        List<TriggeredRuleDto> triggered = new ArrayList<>();
        if (tx.getTriggeredRules() != null && !tx.getTriggeredRules().isBlank()) {
            for (String ruleId : tx.getTriggeredRules().split(",")) {
                String rid = ruleId.trim();
                if (rid.isEmpty()) {
                    continue;
                }
                FraudRule rule = ruleIndex.get(rid);
                if (rule != null) {
                    triggered.add(new TriggeredRuleDto(rule.getId(), rule.getName(), rule.getWeight()));
                } else {
                    triggered.add(new TriggeredRuleDto(rid, rid, 0));
                }
            }
        }
        return new TransactionResponse(
                tx.getId(),
                tx.getUserId(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getMerchantId(),
                tx.getCountry(),
                tx.getScore(),
                tx.getVerdict().name(),
                tx.getStatus().name(),
                tx.getCreatedAt().toEpochMilli(),
                triggered
        );
    }

    private String deriveCountry(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return "US";
        }
        // Very rough demo mapping based on coordinates.
        if (latitude > 35 && longitude > 60 && longitude < 100) {
            return "IN";
        }
        if (latitude > 45 && longitude > -10 && longitude < 40) {
            return "DE";
        }
        if (longitude > 120) {
            return "JP";
        }
        return "US";
    }
}
