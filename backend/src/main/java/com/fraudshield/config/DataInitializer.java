package com.fraudshield.config;

import com.fraudshield.model.AppUser;
import com.fraudshield.model.AuditLog;
import com.fraudshield.model.FraudRule;
import com.fraudshield.model.Role;
import com.fraudshield.model.Transaction;
import com.fraudshield.model.TransactionStatus;
import com.fraudshield.repository.AppUserRepository;
import com.fraudshield.repository.AuditLogRepository;
import com.fraudshield.repository.FraudRuleRepository;
import com.fraudshield.repository.TransactionRepository;
import com.fraudshield.service.RulesEngineService;
import com.fraudshield.service.TransactionService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final String[] MERCHANTS = {
            "MERCH-ONLINE-001", "AMZ-RETAIL-44", "FUEL-STN-902",
            "TRAVEL-BKG-17", "ELEC-STORE-28", "FOOD-DEL-55"
    };
    private static final String[] CURRENCIES = {"USD", "EUR", "GBP", "INR", "JPY"};
    private static final String[] COUNTRIES = {"US", "IN", "GB", "DE", "JP", "RU", "NG"};
    private static final String[] USERS_POOL = {
            "u-1023", "u-2041", "u-3399", "u-5582", "u-7120", "u-8841", "u-9002"
    };

    private final AppUserRepository userRepository;
    private final FraudRuleRepository fraudRuleRepository;
    private final TransactionRepository transactionRepository;
    private final AuditLogRepository auditLogRepository;
    private final RulesEngineService rulesEngineService;
    private final TransactionService transactionService;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(AppUserRepository userRepository,
                           FraudRuleRepository fraudRuleRepository,
                           TransactionRepository transactionRepository,
                           AuditLogRepository auditLogRepository,
                           RulesEngineService rulesEngineService,
                           TransactionService transactionService,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.fraudRuleRepository = fraudRuleRepository;
        this.transactionRepository = transactionRepository;
        this.auditLogRepository = auditLogRepository;
        this.rulesEngineService = rulesEngineService;
        this.transactionService = transactionService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            transactionService.primeSequence();
            return;
        }

        seedUsers();
        seedRules();
        seedTransactions();
        seedAuditLogs();
        transactionService.primeSequence();
    }

    private void seedUsers() {
        Instant now = Instant.now();
        userRepository.save(new AppUser("HKadmin@fraudshield.io",
                passwordEncoder.encode("Admin@123"), "HK Admin", Role.ROLE_ADMIN, now));
        userRepository.save(new AppUser("HKanalyst@fraudshield.io",
                passwordEncoder.encode("Analyst@123"), "HK Analyst", Role.ROLE_ANALYST, now));
        userRepository.save(new AppUser("HKsuperadmin@fraudshield.io",
                passwordEncoder.encode("SuperAdmin@123"), "HK SuperAdmin", Role.ROLE_SUPER_ADMIN, now));
    }

    private void seedRules() {
        fraudRuleRepository.save(new FraudRule("velocity", "Velocity Check",
                "Flags > 5 transactions from the same account in 60 seconds", 40, true));
        fraudRuleRepository.save(new FraudRule("amount", "Amount Anomaly",
                "Flags a payment more than 3x the user 30-day average", 30, true));
        fraudRuleRepository.save(new FraudRule("geo", "Geo-Mismatch",
                "Flags when login country differs from transaction country", 20, true));
        fraudRuleRepository.save(new FraudRule("duplicate", "Duplicate Detection",
                "Flags the same amount + merchant within a 10-second window", 10, true));
        fraudRuleRepository.save(new FraudRule("nightowl", "Odd-Hour Spending",
                "Flags large payments between 2 AM and 5 AM", 15, false));
    }

    private void seedTransactions() {
        Random random = new Random(42L);
        Instant now = Instant.now();
        int total = 48;

        for (int i = 0; i < total; i++) {
            Transaction tx = new Transaction();
            tx.setId("tx-" + (10000 + i));
            tx.setUserId(USERS_POOL[random.nextInt(USERS_POOL.length)]);

            // Mix of high and low amounts so the amount rule fires for some.
            boolean bigSpender = random.nextDouble() < 0.4;
            double raw = bigSpender
                    ? 2000 + random.nextInt(13000) + random.nextDouble()
                    : 5 + random.nextInt(900) + random.nextDouble();
            tx.setAmount(BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP));

            tx.setCurrency(CURRENCIES[random.nextInt(CURRENCIES.length)]);
            tx.setMerchantId(MERCHANTS[random.nextInt(MERCHANTS.length)]);
            tx.setCountry(COUNTRIES[random.nextInt(COUNTRIES.length)]);

            long offsetMs = (long) (random.nextDouble() * 3L * 24 * 60 * 60 * 1000);
            tx.setCreatedAt(now.minus(offsetMs, ChronoUnit.MILLIS));
            tx.setConfirmedFraud(false);
            tx.setSeeded(true);

            RulesEngineService.ScoreResult result = rulesEngineService.score(tx);
            tx.setScore(result.score());
            tx.setVerdict(result.verdict());
            tx.setStatus(result.status());
            tx.setTriggeredRules(String.join(",", result.firedRuleIds()));

            // Mark a subset of flagged transactions as OVERRIDDEN for a realistic mix.
            if (tx.getStatus() == TransactionStatus.PENDING && random.nextDouble() < 0.3) {
                tx.setStatus(TransactionStatus.OVERRIDDEN);
            }

            transactionRepository.save(tx);
        }
    }

    private void seedAuditLogs() {
        Instant now = Instant.now();
        List<AuditLog> logs = List.of(
                new AuditLog("HK Analyst", "Overrode verdict on tx-10024 \u2192 FALSE_POSITIVE",
                        "10.2.14.9", "\uD83D\uDD01", now.minus(8, ChronoUnit.MINUTES), true),
                new AuditLog("HK Admin", "Disabled rule \"Odd-Hour Spending\"",
                        "10.2.14.3", "\u2699\uFE0F", now.minus(42, ChronoUnit.MINUTES), true),
                new AuditLog("HK Analyst", "Confirmed fraud on tx-10031",
                        "10.2.14.9", "\uD83D\uDEA8", now.minus(90, ChronoUnit.MINUTES), true),
                new AuditLog("HK SuperAdmin", "Updated Amount Anomaly threshold: 3x \u2192 2.5x",
                        "10.2.14.1", "\u270F\uFE0F", now.minus(180, ChronoUnit.MINUTES), true),
                new AuditLog("HK Admin", "Logged in",
                        "10.2.14.3", "\uD83D\uDD11", now.minus(240, ChronoUnit.MINUTES), true),
                new AuditLog("HK Analyst", "Exported flagged transactions report (CSV)",
                        "10.2.14.9", "\uD83D\uDCC4", now.minus(320, ChronoUnit.MINUTES), true)
        );
        auditLogRepository.saveAll(logs);
    }
}
