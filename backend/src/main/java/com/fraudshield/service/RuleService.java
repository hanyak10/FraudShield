package com.fraudshield.service;

import com.fraudshield.dto.RuleResponse;
import com.fraudshield.dto.RuleUpdateRequest;
import com.fraudshield.exception.NotFoundException;
import com.fraudshield.model.FraudRule;
import com.fraudshield.repository.FraudRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RuleService {

    private final FraudRuleRepository fraudRuleRepository;
    private final AuditService auditService;

    public RuleService(FraudRuleRepository fraudRuleRepository, AuditService auditService) {
        this.fraudRuleRepository = fraudRuleRepository;
        this.auditService = auditService;
    }

    public List<RuleResponse> list() {
        return fraudRuleRepository.findAll().stream()
                .sorted(Comparator.comparingInt(FraudRule::getWeight).reversed())
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public RuleResponse update(String id, RuleUpdateRequest request, String actorName, String ip) {
        FraudRule rule = fraudRuleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Rule not found: " + id));

        StringBuilder action = new StringBuilder();
        if (request.enabled() != null && request.enabled() != rule.isEnabled()) {
            rule.setEnabled(request.enabled());
            action.append(request.enabled() ? "Enabled rule \"" : "Disabled rule \"")
                    .append(rule.getName()).append("\"");
        }
        if (request.weight() != null && request.weight() != rule.getWeight()) {
            if (action.length() > 0) {
                action.append("; ");
            }
            action.append("Updated weight of \"").append(rule.getName())
                    .append("\" to ").append(request.weight());
            rule.setWeight(request.weight());
        }

        fraudRuleRepository.save(rule);
        if (action.length() == 0) {
            action.append("Updated rule \"").append(rule.getName()).append("\"");
        }
        auditService.log(actorName, action.toString(), ip, "\u2699\uFE0F");
        return toResponse(rule);
    }

    private RuleResponse toResponse(FraudRule rule) {
        return new RuleResponse(
                rule.getId(),
                rule.getName(),
                rule.getDescription(),
                rule.getDescription(),
                rule.getWeight(),
                rule.isEnabled()
        );
    }
}
