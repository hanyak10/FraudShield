package com.fraudshield.repository;

import com.fraudshield.model.FraudRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FraudRuleRepository extends JpaRepository<FraudRule, String> {
    List<FraudRule> findByEnabledTrue();
}
