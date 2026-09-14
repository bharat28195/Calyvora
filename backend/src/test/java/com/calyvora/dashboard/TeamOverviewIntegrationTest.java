package com.calyvora.dashboard;

import com.calyvora.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Team overview (founder feedback B): derived attendance, scoped by the reporting tree (PD-32).
 * Uses the demo seed.
 */
class TeamOverviewIntegrationTest extends IntegrationTestBase {

    private Session demo(String email) throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        return login(email, "demopass123");
    }

    @Test
    void owner_sees_headcount_and_present_counts() throws Exception {
        Session owner = demo("ava.chen@northwind.demo");
        // The seed creates no leave, so nobody is out. On a weekday everyone is "present"; on a weekend
        // the day sheet derives WEEK_OFF for all, so present is 0 — assert the value for today.
        boolean weekend = java.time.LocalDate.now().getDayOfWeek().getValue() >= 6;
        mockMvc.perform(get("/api/v1/dashboard/team").header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headcount").value(7))
                .andExpect(jsonPath("$.presentToday").value(weekend ? 0 : 7))
                .andExpect(jsonPath("$.onLeaveToday").value(0));
    }

    @Test
    void hr_sees_the_whole_company() throws Exception {
        Session hr = demo("leo.martins@northwind.demo");
        mockMvc.perform(get("/api/v1/dashboard/team").header("Authorization", "Bearer " + hr.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headcount").value(7));
    }

    @Test
    void a_lead_sees_their_own_downline_and_nothing_else() throws Exception {
        // Tom manages Sara, and only Sara. His overview is a team of one, whatever his role says.
        Session tom = demo("tom.becker@northwind.demo");
        boolean weekend = java.time.LocalDate.now().getDayOfWeek().getValue() >= 6;
        mockMvc.perform(get("/api/v1/dashboard/team").header("Authorization", "Bearer " + tom.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headcount").value(1))
                .andExpect(jsonPath("$.presentToday").value(weekend ? 0 : 1))
                // Sara has a request pending; any leave shown belongs to the downline, never to Ava
                // or Marcus, whose absences are none of Tom's business.
                .andExpect(jsonPath("$.monthLeaves[?(@.employeeName != 'Sara Okoro')]").isEmpty());
    }

    @Test
    void a_member_with_reports_gets_the_panel_because_of_the_tree() throws Exception {
        // Priya is a plain MEMBER and Dev reports to her. That line, not her role, is the grant.
        Session priya = demo("priya.nair@northwind.demo");
        mockMvc.perform(get("/api/v1/dashboard/team").header("Authorization", "Bearer " + priya.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headcount").value(1));
    }

    @Test
    void someone_with_no_reports_is_forbidden() throws Exception {
        Session dev = demo("dev.sharma@northwind.demo");   // the intern: MEMBER, nobody under them
        mockMvc.perform(get("/api/v1/dashboard/team").header("Authorization", "Bearer " + dev.accessToken()))
                .andExpect(status().isForbidden());
    }
}
