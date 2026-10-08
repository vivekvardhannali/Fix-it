package com.fixit;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixit.entity.User;
import com.fixit.repository.UserRepository;

import jakarta.persistence.EntityManager;

/** Checkpoint 20: homepage discovery (union of tags, sorted by "+" count) and the profile list. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DiscoveryApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    RequestPostProcessor alice, bob, carol, dave;
    long onlyA, onlyB, both, neither, onlyAPopular;

    @BeforeEach
    void setUp() throws Exception {
        alice = authentication(TestAuth.as(users.save(new User("alice@smail.iitm.ac.in"))));
        bob = authentication(TestAuth.as(users.save(new User("bob@smail.iitm.ac.in"))));
        carol = authentication(TestAuth.as(users.save(new User("carol@smail.iitm.ac.in"))));
        dave = authentication(TestAuth.as(users.save(new User("dave@smail.iitm.ac.in"))));
        onlyA = question(alice, "Only A", "[\"tech\"]");
        onlyB = question(alice, "Only B", "[\"math\"]");
        both = question(bob, "Both A and B", "[\"tech\",\"math\"]");
        neither = question(bob, "Neither", "[\"code\"]");
        onlyAPopular = question(alice, "Only A, popular", "[\"tech\"]");
    }

    private long question(RequestPostProcessor as, String title, String tags) throws Exception {
        String res = mvc.perform(post("/api/questions").with(as).contentType("application/json")
                .content("{\"title\":\"" + title + "\",\"body\":\"line one\\n\\nline two\",\"tags\":" + tags + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(res).get("questionId").asLong();
    }

    private void plus(RequestPostProcessor as, long question) throws Exception {
        mvc.perform(post("/api/questions/" + question + "/interest").with(as)).andExpect(status().isOk());
    }

    @Test
    void unionOfSelectedTags_notIntersection_sortedByPlusCountDescending() throws Exception {
        plus(bob, onlyB); plus(carol, onlyB); plus(dave, onlyB);          // Only B: 3
        plus(bob, onlyAPopular); plus(carol, onlyAPopular);               // Only A, popular: 2
        plus(dave, both);                                                  // Both: 1
        // Only A: 0, Neither: 0 (not selected)

        mvc.perform(get("/api/questions").param("tags", "tech").param("tags", "math").with(dave))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].questionId",
                        contains((int) onlyB, (int) onlyAPopular, (int) both, (int) onlyA)))  // union: matches A OR B
                .andExpect(jsonPath("$.items[*].interestCount", contains(3, 2, 1, 0)))
                .andExpect(jsonPath("$.totalItems").value(4));
    }

    @Test
    void aQuestionWithBothSelectedTagsAppearsOnlyOnce() throws Exception {
        mvc.perform(get("/api/questions").param("tags", "tech").param("tags", "math").with(dave))
                .andExpect(jsonPath("$.items[?(@.questionId == " + both + ")]", hasSize(1)));
    }

    @Test
    void singleTag_noTags_unknownTagIsRejected_caseInsensitive() throws Exception {
        mvc.perform(get("/api/questions").param("tags", "MATH").with(dave))
                .andExpect(jsonPath("$.items", hasSize(2)));                                    // Only B + Both
        mvc.perform(get("/api/questions").with(dave))
                .andExpect(jsonPath("$.totalItems").value(5));                                  // no tags = everything
        mvc.perform(get("/api/questions").param("tags", "Nope").with(dave))        // not one of tech/math/code/others
                .andExpect(status().isBadRequest());
    }

    @Test
    void ordersFollowPlusClicks_andTiesAreNewestFirst() throws Exception {
        mvc.perform(get("/api/questions").param("tags", "tech").with(dave))                         // all 0: newest first
                .andExpect(jsonPath("$.items[*].questionId", contains((int) onlyAPopular, (int) both, (int) onlyA)));
        plus(bob, onlyA);                                                                        // now the oldest is on top
        mvc.perform(get("/api/questions").param("tags", "tech").with(dave))
                .andExpect(jsonPath("$.items[0].questionId").value(onlyA));
        plus(bob, onlyA);                                                                        // second click removes it again
        mvc.perform(get("/api/questions").param("tags", "tech").with(dave))
                .andExpect(jsonPath("$.items[0].questionId").value(onlyAPopular));
    }

    @Test
    void commentVotesDoNotAffectDiscoveryOrder() throws Exception {
        plus(bob, onlyB);
        String c = mvc.perform(post("/api/questions/" + onlyA + "/comments").with(carol).contentType("application/json")
                .content("{\"body\":\"hello\"}")).andReturn().getResponse().getContentAsString();
        long commentId = new ObjectMapper().readTree(c).get("commentId").asLong();
        mvc.perform(put("/api/comments/" + commentId + "/vote").with(dave).contentType("application/json")
                .content("{\"voteType\":\"UP\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/questions").param("tags", "tech").param("tags", "math").with(dave))
                .andExpect(jsonPath("$.items[0].questionId").value(onlyB));
    }

    @Test
    void summaryShowsPreviewTagsStatusAndCount() throws Exception {
        plus(carol, onlyA);
        mvc.perform(get("/api/questions").param("tags", "tech").param("size", "1").with(dave))
                .andExpect(jsonPath("$.items[0].title").value("Only A"))
                .andExpect(jsonPath("$.items[0].bodyPreview").value("line one\nline two"))
                .andExpect(jsonPath("$.items[0].tags", contains("tech")))
                .andExpect(jsonPath("$.items[0].isResolved").value(false))
                .andExpect(jsonPath("$.items[0].interestCount").value(1))
                .andExpect(jsonPath("$.items[0].authorId").isNumber());
    }

    @Test
    void paging() throws Exception {
        mvc.perform(get("/api/questions").param("size", "2").param("page", "0").with(dave))
                .andExpect(jsonPath("$.items", hasSize(2))).andExpect(jsonPath("$.totalItems").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
        mvc.perform(get("/api/questions").param("size", "2").param("page", "2").with(dave))
                .andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(get("/api/questions").param("size", "2").param("page", "9").with(dave))
                .andExpect(jsonPath("$.items", empty()));
    }

    @Test
    void invalidParametersAre400_andLoginIsRequired() throws Exception {
        mvc.perform(get("/api/questions").param("page", "-1").with(dave)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/questions").param("size", "0").with(dave)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/questions").param("size", "51").with(dave)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/questions").param("page", "abc").with(dave)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/questions").param("tags", " ").with(dave)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/questions")).andExpect(status().isUnauthorized());
    }

    @Test
    void profileListsOnlyMyOwnQuestionsNewestFirst() throws Exception {
        mvc.perform(get("/api/me/questions").with(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[*].questionId", contains((int) onlyAPopular, (int) onlyB, (int) onlyA)));
        mvc.perform(get("/api/me/questions").with(carol)).andExpect(jsonPath("$.items", empty()));
        mvc.perform(get("/api/me/questions")).andExpect(status().isUnauthorized());
    }
}
