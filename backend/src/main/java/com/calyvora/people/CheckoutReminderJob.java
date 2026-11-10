package com.calyvora.people;

import com.calyvora.common.config.AppProperties;
import com.calyvora.common.security.TenantBinder;
import com.calyvora.company.Company;
import com.calyvora.company.CompanyRepository;
import com.calyvora.company.CompanySettings;
import com.calyvora.company.CompanySettingsRepository;
import com.calyvora.email.EmailService;
import com.calyvora.identity.UserRepository;
import com.calyvora.notification.NotificationService;
import com.calyvora.notification.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * "You didn't check out yesterday" (PD-67, feedback item 3).
 *
 * <p>Runs every hour; for each company it acts only in the hour that is 8 a.m. in that company's own
 * timezone, so everyone is reminded at the start of their next working morning rather than at
 * midnight UTC. Whoever left a session open the day before gets an email and an inbox note, each
 * linking straight to the correction form. Each session is marked once reminded, so a restart or a
 * second instance never sends it twice.
 */
@Component
public class CheckoutReminderJob {

    private static final Logger log = LoggerFactory.getLogger(CheckoutReminderJob.class);
    static final int SEND_AT_HOUR = 8;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final CompanyRepository companyRepository;
    private final CompanySettingsRepository settingsRepository;
    private final AttendancePunchRepository punchRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationService notifications;
    private final TenantBinder tenantBinder;
    private final TransactionTemplate tx;
    private final AppProperties props;
    private final boolean enabled;

    public CheckoutReminderJob(CompanyRepository companyRepository, CompanySettingsRepository settingsRepository,
                               AttendancePunchRepository punchRepository, EmployeeRepository employeeRepository,
                               UserRepository userRepository, EmailService emailService, NotificationService notifications,
                               TenantBinder tenantBinder, TransactionTemplate tx, AppProperties props,
                               @Value("${calyvora.jobs.checkout-reminder:true}") boolean enabled) {
        this.companyRepository = companyRepository;
        this.settingsRepository = settingsRepository;
        this.punchRepository = punchRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.notifications = notifications;
        this.tenantBinder = tenantBinder;
        this.tx = tx;
        this.props = props;
        this.enabled = enabled;
    }

    @Scheduled(cron = "0 5 * * * *")
    public void hourly() {
        if (!enabled) return;
        try {
            run(Instant.now());
        } catch (RuntimeException e) {
            log.warn("Checkout reminders failed this hour: {}", e.toString());
        }
    }

    /** Remind every company whose local time is 8 a.m. at {@code now}. Returns how many were reminded. */
    public int run(Instant now) {
        int sent = 0;
        for (Company company : companyRepository.findAll()) {
            if (company.isPlatform()) continue;
            try {
                Integer n = tx.execute(status -> tenantBinder.callAs(company.getId(), () -> remind(company, now)));
                sent += n == null ? 0 : n;
            } catch (RuntimeException e) {
                log.warn("Checkout reminders failed for company {}: {}", company.getId(), e.toString());
            }
        }
        return sent;
    }

    private int remind(Company company, Instant now) {
        CompanySettings settings = settingsRepository.findById(company.getId()).orElse(null);
        ZoneId zone;
        try {
            zone = ZoneId.of(settings == null || settings.getTimezone() == null ? "Asia/Kolkata" : settings.getTimezone());
        } catch (RuntimeException e) {
            zone = ZoneId.of("Asia/Kolkata");
        }
        ZonedDateTime local = now.atZone(zone);
        if (local.getHour() != SEND_AT_HOUR) return 0;
        LocalDate yesterday = local.toLocalDate().minusDays(1);

        // One reminder per person, for their earliest open session of the day.
        Map<UUID, AttendancePunch> open = new LinkedHashMap<>();
        for (AttendancePunch p : punchRepository.findByCompanyIdAndDateAndCheckOutIsNullAndRemindedAtIsNull(company.getId(), yesterday)) {
            open.merge(p.getEmployeeId(), p, (a, b) -> a.getCheckIn().isBefore(b.getCheckIn()) ? a : b);
        }
        List<AttendancePunch> all = punchRepository.findByCompanyIdAndDateAndCheckOutIsNullAndRemindedAtIsNull(company.getId(), yesterday);
        int sent = 0;
        String day = yesterday.format(DAY);
        String link = props.frontendBaseUrl() + "/me/attendance?fix=" + yesterday;
        for (AttendancePunch p : open.values()) {
            Employee e = employeeRepository.findByIdAndCompanyId(p.getEmployeeId(), company.getId()).orElse(null);
            if (e == null || e.getUserId() == null) continue;
            var user = userRepository.findByIdAndCompanyId(e.getUserId(), company.getId()).orElse(null);
            if (user == null) continue;
            String checkIn = p.getCheckIn().format(TIME);
            emailService.sendCheckoutReminder(user.getEmail(), user.getFirstName(), company.getName(), day, checkIn, link);
            notifications.send(company.getId(), user.getId(), null, NotificationType.CHECKOUT_MISSED,
                    "You didn't check out on " + day, "Checked in at " + checkIn + ". Add your check-out time.",
                    "/me/attendance?fix=" + yesterday, "ATTENDANCE", p.getId());
            sent++;
        }
        for (AttendancePunch p : all) p.setRemindedAt(now);
        punchRepository.saveAll(all);
        return sent;
    }
}
