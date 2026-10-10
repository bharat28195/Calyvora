package com.calyvora.people;

import com.calyvora.common.security.TenantBinder;
import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "You didn't check out" (PD-67): sent at 8 a.m. company time, once, to the right person. */
class CheckoutReminderJobTest extends IntegrationTestBase {

    @Autowired
    private CheckoutReminderJob job;
    @Autowired
    private AttendancePunchRepository punches;
    @Autowired
    private TenantBinder tenantBinder;
    @Autowired
    private TransactionTemplate tx;

    @Test
    @DisplayName("an open session from yesterday is reminded at 8 a.m. local time, once, by email and in the inbox")
    void reminds_once_at_eight() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session priya = login("priya.nair@northwind.demo", "demopass123");
        JsonNode me = getJson("/api/v1/auth/me", priya);
        UUID companyId = UUID.fromString(me.get("company").get("id").asText());
        UUID employeeId = null;
        for (JsonNode e : getJson("/api/v1/people/employees", login("ava.chen@northwind.demo", "demopass123"))) {
            if (e.toString().contains("Priya")) employeeId = UUID.fromString(e.get("id").asText());
        }
        assertThat(employeeId).isNotNull();

        ZoneId india = ZoneId.of("Asia/Kolkata");
        LocalDate today = LocalDate.now(india);
        UUID emp = employeeId;
        tx.executeWithoutResult(s -> tenantBinder.callAs(companyId, () -> {
            punches.deleteByEmployeeIdAndDate(emp, today.minusDays(1));
            return punches.save(new AttendancePunch(UUID.randomUUID(), companyId, emp, today.minusDays(1), LocalTime.of(9, 42)));
        }));

        int before = email().checkoutReminders().size();
        // 7:30 is not the hour: nothing goes.
        job.run(today.atTime(7, 30).atZone(india).toInstant());
        assertThat(email().checkoutReminders()).hasSize(before);

        job.run(today.atTime(8, 30).atZone(india).toInstant());
        assertThat(email().checkoutReminders()).hasSize(before + 1);
        assertThat(email().checkoutReminders().get(before).to()).isEqualTo("priya.nair@northwind.demo");
        assertThat(email().checkoutReminders().get(before).url()).endsWith("/me/attendance?fix=" + today.minusDays(1));
        assertThat(getJson("/api/v1/notifications", priya).toString()).contains("CHECKOUT_MISSED");

        // Run again in the same hour (a restart): nobody is reminded twice.
        job.run(today.atTime(8, 50).atZone(india).toInstant());
        assertThat(email().checkoutReminders()).hasSize(before + 1);
    }
}
