package com.calyvora.feed;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The company feed: posting, visibility, reactions, comments and pinning. */
class FeedIntegrationTest extends IntegrationTestBase {

    private static final String DEMO_PW = "demopass123";

    @Test
    void a_company_post_is_visible_to_everyone() throws Exception {
        seedDemo();
        Session owner = login("ava.chen@northwind.demo", DEMO_PW);
        write(owner, "Welcome to the new starters joining this week! 🎉", "CELEBRATION", "COMPANY", null);

        Session member = login("sara.okoro@northwind.demo", DEMO_PW);
        assertThat(bodies(getJson("/api/v1/feed", member))).anyMatch(b -> b.contains("new starters"));
    }

    @Test
    void a_team_post_is_hidden_from_other_teams_but_visible_to_the_team_and_admins() throws Exception {
        seedDemo();
        Session owner = login("ava.chen@northwind.demo", DEMO_PW);
        String engineeringId = departmentIdNamed(owner, "Engineering");

        // Priya (Engineering, MEMBER) posts to her own team.
        Session priya = login("priya.nair@northwind.demo", DEMO_PW);
        write(priya, "Engineering-only: RLS rollout notes", "UPDATE", "DEPARTMENT", engineeringId);

        // Another engineer sees it.
        Session marcus = login("marcus.reed@northwind.demo", DEMO_PW);
        assertThat(bodies(getJson("/api/v1/feed", marcus))).anyMatch(b -> b.contains("RLS rollout"));

        // Someone in Customer Support does not.
        Session sara = login("sara.okoro@northwind.demo", DEMO_PW);
        assertThat(bodies(getJson("/api/v1/feed", sara))).noneMatch(b -> b.contains("RLS rollout"));

        // The owner does, because admins can see everything.
        assertThat(bodies(getJson("/api/v1/feed", owner))).anyMatch(b -> b.contains("RLS rollout"));
    }

    @Test
    void a_team_post_needs_a_team() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", "password1234");
        Map<String, String> body = new HashMap<>();
        body.put("body", "Secret");
        body.put("visibility", "DEPARTMENT");
        mockMvc.perform(post("/api/v1/feed").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reacting_twice_with_the_same_emoji_removes_it() throws Exception {
        seedDemo();
        Session owner = login("ava.chen@northwind.demo", DEMO_PW);
        String postId = write(owner, "We shipped Orbit 1.0", "ANNOUNCEMENT", "COMPANY", null);

        Session sara = login("sara.okoro@northwind.demo", DEMO_PW);
        JsonNode after = react(sara, postId, "🎉");
        assertThat(after.get("reactions").get("🎉").asInt()).isEqualTo(1);
        assertThat(after.get("myReactions").toString()).contains("🎉");

        JsonNode toggled = react(sara, postId, "🎉");
        assertThat(toggled.get("reactions").has("🎉")).isFalse();
        assertThat(toggled.get("myReactions").size()).isZero();
    }

    @Test
    void comments_belong_to_their_author_and_admins_can_moderate() throws Exception {
        seedDemo();
        Session owner = login("ava.chen@northwind.demo", DEMO_PW);
        String postId = write(owner, "What should we build next?", "QUESTION", "COMPANY", null);

        Session sara = login("sara.okoro@northwind.demo", DEMO_PW);
        JsonNode withComment = comment(sara, postId, "A mobile app, please");
        assertThat(withComment.get("comments").size()).isEqualTo(1);
        String commentId = withComment.get("comments").get(0).get("id").asText();

        // Someone else's comment isn't theirs to delete…
        Session leo = login("leo.martins@northwind.demo", DEMO_PW);
        mockMvc.perform(delete("/api/v1/feed/comments/" + commentId).header("Authorization", bearer(leo)))
                .andExpect(status().isForbidden());

        // …but an admin can moderate it.
        mockMvc.perform(delete("/api/v1/feed/comments/" + commentId).header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
    }

    @Test
    void only_admins_pin_and_pinned_posts_come_first() throws Exception {
        seedDemo();
        Session owner = login("ava.chen@northwind.demo", DEMO_PW);
        write(owner, "Just a normal update", "UPDATE", "COMPANY", null);
        String importantId = write(owner, "Office closed on Friday", "ANNOUNCEMENT", "COMPANY", null);

        Session sara = login("sara.okoro@northwind.demo", DEMO_PW);
        mockMvc.perform(post("/api/v1/feed/" + importantId + "/pin").header("Authorization", bearer(sara))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("pinned", true))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/feed/" + importantId + "/pin").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("pinned", true))))
                .andExpect(status().isOk());

        assertThat(getJson("/api/v1/feed", sara).get(0).get("body").asText()).isEqualTo("Office closed on Friday");
    }

    @Test
    void you_can_delete_your_own_post_but_not_someone_elses() throws Exception {
        seedDemo();
        Session priya = login("priya.nair@northwind.demo", DEMO_PW);
        String postId = write(priya, "My post", "UPDATE", "COMPANY", null);

        Session leo = login("leo.martins@northwind.demo", DEMO_PW);
        mockMvc.perform(delete("/api/v1/feed/" + postId).header("Authorization", bearer(leo)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/feed/" + postId).header("Authorization", bearer(priya)))
                .andExpect(status().isNoContent());
        assertThat(bodies(getJson("/api/v1/feed", priya))).noneMatch(b -> b.equals("My post"));
    }

    @Test
    void the_feed_is_tenant_isolated() throws Exception {
        Session a = onboardOwner("Company A", "a@a.com", "password1234");
        write(a, "A-only news", "UPDATE", "COMPANY", null);

        Session b = onboardOwner("Company B", "b@b.com", "password1234");
        assertThat(getJson("/api/v1/feed", b).size()).isZero();
    }

    // ---- helpers ----

    private void seedDemo() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
    }

    private String bearer(Session s) {
        return "Bearer " + s.accessToken();
    }

    private String write(Session s, String body, String kind, String visibility, String departmentId)
            throws Exception {
        Map<String, String> payload = new HashMap<>();
        payload.put("body", body);
        payload.put("kind", kind);
        payload.put("visibility", visibility);
        if (departmentId != null) payload.put("departmentId", departmentId);
        MvcResult res = mockMvc.perform(post("/api/v1/feed").header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private JsonNode react(Session s, String postId, String emoji) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/feed/" + postId + "/react")
                        .header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("emoji", emoji))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private JsonNode comment(Session s, String postId, String body) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/feed/" + postId + "/comments")
                        .header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("body", body))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private java.util.List<String> bodies(JsonNode feed) {
        java.util.List<String> out = new java.util.ArrayList<>();
        feed.forEach(p -> out.add(p.get("body").asText()));
        return out;
    }

    private String departmentIdNamed(Session s, String name) throws Exception {
        for (JsonNode d : getJson("/api/v1/people/departments", s)) {
            if (name.equals(d.get("name").asText())) {
                return d.get("id").asText();
            }
        }
        throw new AssertionError("no department named " + name);
    }
}
