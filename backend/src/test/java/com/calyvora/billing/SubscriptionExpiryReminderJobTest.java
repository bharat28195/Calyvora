package com.calyvora.billing;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Your subscription ends in 5 days" (PD-76): to the admins, once per end date, never too early. */
class SubscriptionExpiryReminderJobTest extends IntegrationTestBase {

    @Autowired
    private SubscriptionExpiryReminderJob job;
    @Autowired
    private SubscriptionRepository subscriptions;

    @Test
    @DisplayName("admins are reminded five days ahead by email and inbox, once, and again after an extension")
    void reminds_admins_five_days_ahead() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session ava = login("ava.chen@northwind.demo", "demopass123");
        UUID companyId = UUID.fromString(getJson("/api/v1/auth/me", ava).get("company").get("id").asText());
        LocalDate today = LocalDate.now();

        Subscription sub = subscriptions.findByCompanyId(companyId).orElseThrow();
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setExpiryRemindedFor(null);
        sub.setEndsAt(today.plusDays(9));
        subscriptions.save(sub);

        // Nine days out is too early.
        int before = email().subscriptionEndings().size();
        job.run(today);
        assertThat(email().subscriptionEndings()).hasSize(before);

        // Five days out: every active admin hears, nobody else.
        sub = subscriptions.findByCompanyId(companyId).orElseThrow();
        sub.setEndsAt(today.plusDays(5));
        subscriptions.save(sub);
        job.run(today);
        assertThat(email().subscriptionEndings()).extracting(com.calyvora.support.RecordingEmailService.Sent::to)
                .contains("ava.chen@northwind.demo", "marcus.reed@northwind.demo")
                .doesNotContain("priya.nair@northwind.demo");
        int afterFirst = email().subscriptionEndings().size();
        JsonNode inbox = getJson("/api/v1/notifications", ava);
        assertThat(inbox.toString()).contains("SUBSCRIPTION_ENDING").contains("ends in 5 days");
        assertThat(getJson("/api/v1/notifications", login("priya.nair@northwind.demo", "demopass123")).toString())
                .doesNotContain("SUBSCRIPTION_ENDING");

        // The next hours and days: not again for the same date.
        job.run(today);
        job.run(today.plusDays(2));
        assertThat(email().subscriptionEndings()).hasSize(afterFirst);

        // Extended by the owner, then nearing the new date: reminded for that one.
        sub = subscriptions.findByCompanyId(companyId).orElseThrow();
        sub.setEndsAt(today.plusDays(35));
        subscriptions.save(sub);
        job.run(today.plusDays(31));
        assertThat(email().subscriptionEndings()).hasSizeGreaterThan(afterFirst);
    }
}
