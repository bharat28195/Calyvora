package com.calyvora.dashboard;

import com.calyvora.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Owner/Admin team overview (founder feedback B): derived attendance + RBAC. Uses the demo seed.
 */
class TeamOverviewIntegrationTest extends IntegrationTestBase {

    private Session demo(String email) throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        return login(email, "demopass123");
    }

    @Test
    void owner_sees_headcount_and_present_counts() throws Exception {
        Session owner = demo("ava.chen@northwind.demo");
        mockMvc.perform(get("/api/v1/dashboard/team").header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headcount").value(6))
                .andExpect(jsonPath("$.presentToday").value(6))   // seed creates no leave → everyone present
                .andExpect(jsonPath("$.onLeaveToday").value(0));
    }

    @Test
    void member_is_forbidden() throws Exception {
        Session member = demo("priya.nair@northwind.demo");   // MEMBER role
        mockMvc.perform(get("/api/v1/dashboard/team").header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isForbidden());
    }
}
