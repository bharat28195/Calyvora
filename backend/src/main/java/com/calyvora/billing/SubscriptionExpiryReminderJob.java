package com.calyvora.billing;

import com.calyvora.common.config.AppProperties;
import com.calyvora.common.security.TenantBinder;
import com.calyvora.company.Company;
import com.calyvora.company.CompanyRepository;
import com.calyvora.email.EmailService;
import com.calyvora.identity.Role;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.identity.UserStatus;
import com.calyvora.notification.NotificationService;
import com.calyvora.notification.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * "Your subscription ends in 5 days" (PD-76). When a subscription's end date passes, the workspace is
 * locked ({@link Subscription#isLocked()}); this makes sure the company's admins hear about it first,
 * by email and in their inbox, rather than finding out from the lock screen.
 *
 * <p>Runs hourly but sends once per end date: the reminder is due from five days out and remembered in
 * {@code expiry_reminded_for}. Checking "five days or fewer, not yet reminded" instead of "exactly five
 * days" means a server that was asleep on the day (the demo sleeps) still sends it when it wakes, and an
 * owner extending the subscription gets a fresh reminder for the new date.
 */
@Component
public class SubscriptionExpiryReminderJob {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionExpiryReminderJob.class);
    static final int REMIND_DAYS_BEFORE = 5;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private final SubscriptionRepository subscriptionRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationService notifications;
    private final TenantBinder tenantBinder;
    private final TransactionTemplate tx;
    private final AppProperties props;
    private final boolean enabled;

    public SubscriptionExpiryReminderJob(SubscriptionRepository subscriptionRepository, CompanyRepository companyRepository,
                                         UserRepository userRepository, EmailService emailService,
                                         NotificationService notifications, TenantBinder tenantBinder,
                                         TransactionTemplate tx, AppProperties props,
                                         @Value("${calyvora.jobs.subscription-expiry-reminder:true}") boolean enabled) {
        this.subscriptionRepository = subscriptionRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.notifications = notifications;
        this.tenantBinder = tenantBinder;
        this.tx = tx;
        this.props = props;
        this.enabled = enabled;
    }

    @Scheduled(cron = "0 15 * * * *")
    public void hourly() {
        if (!enabled) return;
        try {
            run(LocalDate.now());
        } catch (RuntimeException e) {
            log.warn("Subscription expiry reminders failed this hour: {}", e.toString());
        }
    }

    /** Remind every company whose subscription ends within five days of {@code today}. Returns how many. */
    public int run(LocalDate today) {
        int reminded = 0;
        for (Subscription sub : subscriptionRepository.findAll()) {
            if (!isDue(sub, today)) continue;
            Company company = companyRepository.findById(sub.getCompanyId()).orElse(null);
            if (company == null || company.isPlatform() || company.isArchived()) continue;
            try {
                tx.executeWithoutResult(s -> tenantBinder.callAs(company.getId(), () -> {
                    remind(company, sub, today);
                    return null;
                }));
                reminded++;
            } catch (RuntimeException e) {
                log.warn("Subscription expiry reminder failed for company {}: {}", company.getId(), e.toString());
            }
        }
        return reminded;
    }

    static boolean isDue(Subscription sub, LocalDate today) {
        if (sub.getEndsAt() == null || sub.isLocked()) return false;
        if (sub.getEndsAt().equals(sub.getExpiryRemindedFor())) return false;
        long daysLeft = ChronoUnit.DAYS.between(today, sub.getEndsAt());
        return daysLeft >= 0 && daysLeft <= REMIND_DAYS_BEFORE;
    }

    private void remind(Company company, Subscription sub, LocalDate today) {
        long daysLeft = ChronoUnit.DAYS.between(today, sub.getEndsAt());
        String endsOn = sub.getEndsAt().format(DATE);
        String when = daysLeft == 0 ? "today" : daysLeft == 1 ? "tomorrow" : "in " + daysLeft + " days";
        String link = props.frontendBaseUrl() + "/subscription";
        for (User admin : userRepository.findByCompanyIdOrderByCreatedAtAsc(company.getId())) {
            if (admin.getRole() != Role.ADMIN || admin.getStatus() != UserStatus.ACTIVE) continue;
            emailService.sendSubscriptionEnding(admin.getEmail(), admin.getFirstName(), company.getName(),
                    endsOn, daysLeft, link);
            notifications.send(company.getId(), admin.getId(), null, NotificationType.SUBSCRIPTION_ENDING,
                    "Your subscription ends " + when,
                    "Orbit stops working for your team after " + endsOn + " unless it is renewed. Your data stays safe.",
                    "/subscription", "SUBSCRIPTION", sub.getId());
        }
        sub.setExpiryRemindedFor(sub.getEndsAt());
        subscriptionRepository.save(sub);
    }
}
