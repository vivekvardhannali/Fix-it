package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixit.entity.User;
import com.fixit.repository.UserRepository;

import jakarta.persistence.EntityManager;

/** Checkpoint 11: UP / DOWN / REMOVE / SWITCH on comments. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommentVoteApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    RequestPostProcessor alice, bob, carol;
    long aliceId, bobId;
    long questionId, commentId;

    @BeforeEach
    void setUp() throws Exception {
        User a = users.save(new User("alice@smail.iitm.ac.in"));
        User b = users.save(new User("bob@smail.iitm.ac.in"));
        User c = users.save(new User("carol@smail.iitm.ac.in"));
        aliceId = a.getId();
        bobId = b.getId();
        alice = authentication(TestAuth.as(a));
        bob = authentication(TestAuth.as(b));
        carol = authentication(TestAuth.as(c));
        questionId = create("/api/questions", alice, "{\"title\":\"t\",\"body\":\"b\",\"tags\":[\"tech\"]}", "questionId");
        commentId = create("/api/questions/" + questionId + "/comments", bob, "{\"body\":\"c\"}", "commentId");
    }

    private long create(String url, RequestPostProcessor as, String json, String idField) throws Exception {
        String res = mvc.perform(post(url).with(as).contentType("application/json").content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(res).get(idField).asLong();
    }

    private ResultActions click(RequestPostProcessor as, String type) throws Exception {
        return mvc.perform(put("/api/comments/" + commentId + "/vote").with(as)
                .contentType("application/json").content("{\"voteType\":\"" + type + "\"}")).andExpect(status().isOk());
    }

    private void expect(ResultActions r, long up, long down, String mine) throws Exception {
        r.andExpect(jsonPath("$.upCount").value(up)).andExpect(jsonPath("$.downCount").value(down));
        if (mine == null) {
            r.andExpect(jsonPath("$.myVote").doesNotExist());
        } else {
            r.andExpect(jsonPath("$.myVote").value(mine));
        }
    }

    private List<String> dbVotes() {
        em.flush(); // raw SQL: what is really stored for this user's votes
        return jdbc.queryForList("select user_id || ':' || vote_type from question_comment_votes "
                + "where comment_id = ? order by user_id", String.class, commentId);
    }

    @Test
    void noVoteToUp_thenClickUpAgainRemoves() throws Exception {
        expect(click(alice, "UP"), 1, 0, "UP");
        assertThat(dbVotes()).containsExactly(aliceId + ":UP");
        expect(click(alice, "UP"), 0, 0, null);
        assertThat(dbVotes()).isEmpty();
    }

    @Test
    void noVoteToDown_thenClickDownAgainRemoves() throws Exception {
        expect(click(alice, "DOWN"), 0, 1, "DOWN");
        assertThat(dbVotes()).containsExactly(aliceId + ":DOWN");
        expect(click(alice, "DOWN"), 0, 0, null);
        assertThat(dbVotes()).isEmpty();
    }

    @Test
    void switchUpToDownAndBack_keepsSingleRow() throws Exception {
        click(alice, "UP");
        expect(click(alice, "DOWN"), 0, 1, "DOWN");
        assertThat(dbVotes()).containsExactly(aliceId + ":DOWN");
        expect(click(alice, "UP"), 1, 0, "UP");
        assertThat(dbVotes()).containsExactly(aliceId + ":UP");
    }

    @Test
    void usersVoteIndependently_andThreadShowsCountsAndMyVote() throws Exception {
        click(alice, "UP");
        click(bob, "UP");      // the comment's own author may vote too
        click(carol, "DOWN");
        assertThat(dbVotes()).hasSize(3);

        mvc.perform(get("/api/questions/" + questionId + "/comments").with(carol))
                .andExpect(jsonPath("$[0].upCount").value(2))
                .andExpect(jsonPath("$[0].downCount").value(1))
                .andExpect(jsonPath("$[0].myVote").value("DOWN"));
        mvc.perform(get("/api/questions/" + questionId + "/comments").with(alice))
                .andExpect(jsonPath("$[0].myVote").value("UP"));
    }

    @Test
    void explicitRemoveIsIdempotent() throws Exception {
        click(alice, "UP");
        mvc.perform(delete("/api/comments/" + commentId + "/vote").with(alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.upCount").value(0));
        mvc.perform(delete("/api/comments/" + commentId + "/vote").with(alice)).andExpect(status().isOk());
        assertThat(dbVotes()).isEmpty();
    }

    @Test
    void votingDoesNotAffectInterestOrderingOrQuestion() throws Exception {
        long second = create("/api/questions/" + questionId + "/comments", alice, "{\"body\":\"later comment\"}", "commentId");
        em.flush();
        Map<String, Object> before = jdbc.queryForMap(
                "select interest_count, updated_at, is_resolved from questions where question_id = ?", questionId);

        click(alice, "DOWN");
        click(carol, "DOWN");
        mvc.perform(put("/api/comments/" + second + "/vote").with(alice).contentType("application/json")
                .content("{\"voteType\":\"UP\"}")).andExpect(status().isOk());
        em.flush();

        assertThat(jdbc.queryForMap("select interest_count, updated_at, is_resolved from questions where question_id = ?",
                questionId)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from question_interests", Integer.class)).isZero();
        // order is still chronological even though the 2nd comment is "better" voted
        mvc.perform(get("/api/questions/" + questionId + "/comments").with(alice))
                .andExpect(jsonPath("$[0].commentId").value(commentId))
                .andExpect(jsonPath("$[1].commentId").value(second));
    }

    @Test
    void invalidMissingUnauthenticated() throws Exception {
        for (String body : new String[] { "{\"voteType\":\"SIDEWAYS\"}", "{\"voteType\":\"up\"}", "{}", "{\"voteType\":null}" }) {
            mvc.perform(put("/api/comments/" + commentId + "/vote").with(alice).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(put("/api/comments/999999/vote").with(alice).contentType("application/json")
                .content("{\"voteType\":\"UP\"}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/comments/999999/vote").with(alice)).andExpect(status().isNotFound());
        mvc.perform(put("/api/comments/" + commentId + "/vote").contentType("application/json")
                .content("{\"voteType\":\"UP\"}")).andExpect(status().isUnauthorized());
        assertThat(dbVotes()).isEmpty();
    }
}
