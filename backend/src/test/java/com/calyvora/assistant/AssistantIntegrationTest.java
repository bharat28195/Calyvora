package com.calyvora.assistant;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The cross-app assistant answers from the tenant's real data. With no API key configured (as in
 * tests) it runs the offline grounded provider — which must never fabricate and must stay tenant-scoped.
 */
class AssistantIntegrationTest extends IntegrationTestBase {

    private Session demoOwner() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        return login("ava.chen@northwind.demo", "demopass123");
    }

    private JsonNode ask(Session s, String question) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/assistant/ask")
                        .header("Authorization", "Bearer " + s.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("question", question))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    @Test
    void answers_count_questions_from_real_metrics() throws Exception {
        Session owner = demoOwner();
        JsonNode res = ask(owner, "How many open tickets do we have?");
        assertThat(res.get("mode").asText()).isEqualTo("local");
        // The seed creates 3 open tickets.
        assertThat(res.get("answer").asText()).contains("3");
    }

    @Test
    void answers_knowledge_questions_with_grounded_sources() throws Exception {
        Session owner = demoOwner();
        JsonNode res = ask(owner, "How does our authentication and key rotation work?");
        // Should surface the auth handbook page as a source and quote it.
        assertThat(res.get("answer").asText()).containsIgnoringCase("rotation");
        assertThat(res.get("sources")).isNotEmpty();
        assertThat(res.get("sources").get(0).get("kind").asText()).isEqualTo("page");
    }

    @Test
    void is_tenant_scoped() throws Exception {
        demoOwner();   // seeds Northwind
        Session outsider = onboardOwner("Acme", "owner@acme.com", "password1234");
        JsonNode res = ask(outsider, "How many team members are there?");
        // The outsider is a company of one — must not see Northwind's 6.
        assertThat(res.get("answer").asText()).contains("1");
    }

    // ---- reading the whole app, not just Work and Knowledge ----

    @Test
    void answers_about_hiring_which_it_could_not_see_before() throws Exception {
        Session owner = demoOwner();

        // Measure the change rather than the absolute number. The demo seed already posts openings,
        // and pinning this to "3" would make the test fail the next time the seed gains a role —
        // a green suite is worth nothing if it breaks on data it was never testing.
        long before = countIn(ask(owner, "How many open roles do we have?"));

        mockMvc.perform(post("/api/v1/recruit/jobs")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Warehouse Supervisor", "positions", 1))))
                .andExpect(status().isCreated());

        JsonNode res = ask(owner, "How many open roles do we have?");
        assertThat(res.get("answer").asText()).contains("open role");
        assertThat(countIn(res)).isEqualTo(before + 1);
    }

    /** The number the grounded provider emphasised, e.g. "You have **3** open roles." */
    private long countIn(JsonNode res) {
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("\\*\\*(\\d+)\\*\\*").matcher(res.get("answer").asText());
        assertThat(m.find()).as("answer should quote a number: %s", res.get("answer").asText()).isTrue();
        return Long.parseLong(m.group(1));
    }

    @Test
    void points_at_the_module_behind_the_answer() throws Exception {
        Session owner = demoOwner();
        JsonNode res = ask(owner, "How many time off requests are waiting?");

        // A leave question used to end at a knowledge page or nothing at all. It should now offer
        // the screen where the work is actually done.
        boolean linksToTimeOff = false;
        for (JsonNode s : res.get("sources")) {
            if ("module".equals(s.get("kind").asText()) && s.get("href").asText().contains("leave")) {
                linksToTimeOff = true;
            }
        }
        assertThat(linksToTimeOff).isTrue();
    }

    // ---- the scoping rule: the assistant must not be a way around the nav's role gates ----

    @Test
    void a_member_is_refused_hr_only_figures_rather_than_told_zero() throws Exception {
        demoOwner();   // seeds Northwind, including Priya as a MEMBER
        Session member = login("priya.nair@northwind.demo", "demopass123");

        JsonNode res = ask(member, "How many people are on notice?");
        String answer = res.get("answer").asText();

        // The failure this guards against is subtle: with the metric simply absent from the map, a
        // naive getOrDefault would answer "You have 0 people serving notice." That is a confident,
        // wrong answer to someone who is merely not allowed to know — worse than a refusal, because
        // it is indistinguishable from the truth.
        assertThat(answer).doesNotContain("**0**");
        assertThat(answer).containsIgnoringCase("can't see that");
    }

    @Test
    void a_member_still_gets_the_company_wide_answers_they_are_entitled_to() throws Exception {
        demoOwner();
        Session member = login("priya.nair@northwind.demo", "demopass123");

        // The directory is visible to everyone in the app, so the assistant must not become stricter
        // than the product it is answering for.
        JsonNode res = ask(member, "How many team members are there?");
        assertThat(res.get("answer").asText()).containsIgnoringCase("team member");
        assertThat(res.get("answer").asText()).doesNotContainIgnoringCase("can't see that");
    }
}
