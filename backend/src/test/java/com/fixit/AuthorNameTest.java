package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixit.entity.User;
import com.fixit.repository.UserRepository;

/** Every place an author appears shows a display name (the username), never the smail. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthorNameTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ObjectMapper mapper;

    User aliceUser, bobUser, googleUser;
    RequestPostProcessor alice, bob, google;
    long questionId, commentId;

    @BeforeEach
    void setUp() throws Exception {
        aliceUser = users.save(new User("alice.private@smail.iitm.ac.in", "alice_a", null));
        bobUser = users.save(new User("bob.private@smail.iitm.ac.in", "bob_b", null));
        googleUser = users.save(new User("gina.private@smail.iitm.ac.in"));               // no username (e.g. Google sign-in)
        alice = authentication(TestAuth.as(aliceUser));
        bob = authentication(TestAuth.as(bobUser));
        google = authentication(TestAuth.as(googleUser));
        questionId = id(mvc.perform(post("/api/questions").with(alice).contentType("application/json")
                .content("{\"title\":\"WiFi keeps dropping\",\"body\":\"b\",\"tags\":[\"tech\"]}")).andReturn().getResponse().getContentAsString(), "questionId");
    }

    private long id(String json, String field) throws Exception {
        return mapper.readTree(json).get(field).asLong();
    }

    @Test
    void questionPageShowsTheAuthorsName() throws Exception {
        mvc.perform(get("/api/questions/" + questionId).with(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorId").value(aliceUser.getId()))
                .andExpect(jsonPath("$.authorName").value("alice_a"));
    }

    @Test
    void answersAndCommentsAndRepliesShowTheirAuthorsNames() throws Exception {
        mvc.perform(post("/api/questions/" + questionId + "/answers").with(bob).contentType("application/json").content("{\"body\":\"restart it\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.authorName").value("bob_b"));
        mvc.perform(get("/api/questions/" + questionId + "/answers").with(alice))
                .andExpect(jsonPath("$[0].authorName").value("bob_b"));

        commentId = id(mvc.perform(post("/api/questions/" + questionId + "/comments").with(bob).contentType("application/json")
                .content("{\"body\":\"try dns\"}")).andExpect(jsonPath("$.authorName").value("bob_b")).andReturn().getResponse().getContentAsString(), "commentId");
        mvc.perform(post("/api/comments/" + commentId + "/replies").with(alice).contentType("application/json").content("{\"body\":\"no luck\"}"))
                .andExpect(jsonPath("$.authorName").value("alice_a"));
        mvc.perform(get("/api/questions/" + questionId + "/comments").with(alice))
                .andExpect(jsonPath("$[0].authorName").value("bob_b"))
                .andExpect(jsonPath("$[0].replies[0].authorName").value("alice_a"));
        mvc.perform(put("/api/comments/" + commentId).with(bob).contentType("application/json").content("{\"body\":\"edited\"}"))
                .andExpect(jsonPath("$.authorName").value("bob_b"));
    }

    @Test
    void listsShowAuthorNames() throws Exception {
        mvc.perform(get("/api/questions").with(bob)).andExpect(jsonPath("$.items[0].authorName").value("alice_a"));
        mvc.perform(get("/api/me/questions").with(alice)).andExpect(jsonPath("$.items[0].authorName").value("alice_a"));
    }

    @Test
    void anAccountWithoutAUsernameShowsUserPlusIdNotItsSmail() throws Exception {
        long q = id(mvc.perform(post("/api/questions").with(google).contentType("application/json")
                .content("{\"title\":\"t\",\"body\":\"b\",\"tags\":[\"tech\"]}")).andReturn().getResponse().getContentAsString(), "questionId");
        mvc.perform(get("/api/questions/" + q).with(bob))
                .andExpect(jsonPath("$.authorName").value("user" + googleUser.getId()));
    }

    @Test
    void noResponseEverContainsAnotherUsersSmail() throws Exception {
        mvc.perform(post("/api/questions/" + questionId + "/answers").with(bob).contentType("application/json").content("{\"body\":\"x\"}"));
        mvc.perform(post("/api/questions/" + questionId + "/comments").with(bob).contentType("application/json").content("{\"body\":\"x\"}"));
        for (String path : new String[] { "/api/questions/" + questionId, "/api/questions/" + questionId + "/answers",
                "/api/questions/" + questionId + "/comments", "/api/questions", "/api/notifications" }) {
            mvc.perform(get(path).with(alice)).andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("private@smail"))));
        }
    }

    @Test
    void notificationsCarryTheQuestionTitleAndTheActorsName() throws Exception {
        commentId = id(mvc.perform(post("/api/questions/" + questionId + "/comments").with(bob).contentType("application/json")
                .content("{\"body\":\"try dns\"}")).andReturn().getResponse().getContentAsString(), "commentId");
        mvc.perform(get("/api/notifications").with(alice))
                .andExpect(jsonPath("$[0].type").value("COMMENT_ON_QUESTION"))
                .andExpect(jsonPath("$[0].questionTitle").value("WiFi keeps dropping"))
                .andExpect(jsonPath("$[0].actorName").value("bob_b"))
                .andExpect(jsonPath("$[0].actorId").value(bobUser.getId()))
                .andExpect(jsonPath("$[0].commentId").value(commentId));

        mvc.perform(post("/api/comments/" + commentId + "/replies").with(alice).contentType("application/json").content("{\"body\":\"no luck\"}"));
        mvc.perform(get("/api/notifications").with(bob))
                .andExpect(jsonPath("$[0].type").value("REPLY_TO_COMMENT"))
                .andExpect(jsonPath("$[0].questionTitle").value("WiFi keeps dropping"))
                .andExpect(jsonPath("$[0].actorName").value("alice_a"));
        assertThat(aliceUser.getDisplayName()).isEqualTo("alice_a");
    }
}
