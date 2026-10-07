package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
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
import com.fixit.repository.QuestionRepository;
import com.fixit.repository.UserRepository;

import jakarta.persistence.EntityManager;

/** Tags are a FIXED set: tech, math, code, others. Users choose from it; a question needs at least one. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TagApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired QuestionRepository questions;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    RequestPostProcessor alice;
    RequestPostProcessor bob;

    @BeforeEach
    void setUp() {
        alice = authentication(TestAuth.as(users.save(new User("alice@smail.iitm.ac.in"))));
        bob = authentication(TestAuth.as(users.save(new User("bob@smail.iitm.ac.in"))));
    }

    private int ask(RequestPostProcessor as, String tagsJson) throws Exception {
        return mvc.perform(post("/api/questions").with(as).contentType("application/json")
                .content("{\"title\":\"t\",\"body\":\"b\"" + (tagsJson == null ? "" : ",\"tags\":" + tagsJson) + "}"))
                .andReturn().getResponse().getStatus();
    }

    private long createQuestion(RequestPostProcessor as, String tagsJson) throws Exception {
        String res = mvc.perform(post("/api/questions").with(as).contentType("application/json")
                .content("{\"title\":\"t\",\"body\":\"b\",\"tags\":" + tagsJson + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(res).get("questionId").asLong();
    }

    private java.util.List<String> dbTags(long questionId) {
        em.flush();   // raw SQL: what is really stored
        return jdbc.queryForList("select t.name from question_tags qt join tags t using (tag_id) where qt.question_id = ? order by t.name",
                String.class, questionId);
    }

    private int questionCount() {
        em.flush();
        return jdbc.queryForObject("select count(*) from questions", Integer.class);
    }

    @Test
    void theAvailableTagsAreExactlyTechMathCodeOthers_inThatOrder() throws Exception {
        mvc.perform(get("/api/tags").with(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("tech", "math", "code", "others")))
                .andExpect(jsonPath("$[0].tagId").isNumber());
        mvc.perform(get("/api/tags")).andExpect(status().isUnauthorized());
    }

    @Test
    void aQuestionTakesOneToFourOfThem_namesAreCanonicalAndDuplicatesCollapse() throws Exception {
        long id = createQuestion(alice, "[\"TECH\",\"Math\",\" code \",\"tech\"]");     // case/space-insensitive, duplicate dropped
        mvc.perform(get("/api/questions/" + id).with(bob)).andExpect(jsonPath("$.tags", contains("code", "math", "tech")));
        assertThat(dbTags(id)).containsExactly("code", "math", "tech");
        long all = createQuestion(alice, "[\"tech\",\"math\",\"code\",\"others\"]");
        mvc.perform(get("/api/questions/" + all + "/tags").with(bob)).andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void aQuestionWithoutATagIsRejected_andNothingIsSaved() throws Exception {
        int before = questionCount();
        assertThat(ask(alice, null)).isEqualTo(400);                // tags omitted
        assertThat(ask(alice, "[]")).isEqualTo(400);                // tags empty
        mvc.perform(post("/api/questions").with(alice).contentType("application/json").content("{\"title\":\"t\",\"body\":\"b\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Choose at least one tag: tech, math, code, others")));
        assertThat(questionCount()).isEqualTo(before);
    }

    @Test
    void tagsOutsideTheFixedSetAreRejected_andNeverCreated() throws Exception {
        int before = questionCount();
        for (String bad : new String[] { "[\"networking\"]", "[\"tech\",\"hostel\"]", "[\"tech,math\"]", "[\"\"]", "[\"  \"]",
                "[\"" + "x".repeat(60) + "\"]", "[\"tech\",null]" }) {
            assertThat(ask(alice, bad)).as(bad).isEqualTo(400);
        }
        mvc.perform(post("/api/questions").with(alice).contentType("application/json")
                .content("{\"title\":\"t\",\"body\":\"b\",\"tags\":[\"networking\"]}"))
                .andExpect(jsonPath("$.message").value(containsString("Unknown tag 'networking'. Choose from: tech, math, code, others")));
        assertThat(questionCount()).isEqualTo(before);
        em.flush();
        assertThat(jdbc.queryForList("select name from tags order by name", String.class))
                .containsExactly("code", "math", "others", "tech");                      // still only the four
    }

    @Test
    void aTagBelongsToManyQuestions_withoutDuplicatingTheTagRow() throws Exception {
        createQuestion(alice, "[\"tech\"]");
        createQuestion(bob, "[\"tech\",\"math\"]");
        em.flush();
        assertThat(jdbc.queryForObject("select count(*) from tags", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from question_tags qt join tags t using (tag_id) where t.name = 'tech'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void ownerCanReplaceTags_viaTheTagsEndpoint_andItReachesTheDatabase() throws Exception {
        long id = createQuestion(alice, "[\"tech\",\"math\",\"code\"]");
        mvc.perform(put("/api/questions/" + id + "/tags").with(alice).contentType("application/json").content("{\"tags\":[\"math\",\"others\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("math", "others")));
        assertThat(dbTags(id)).containsExactly("math", "others");                      // tech and code really removed
    }

    @Test
    void updatingAQuestion_omittedTagsStay_providedTagsReplace() throws Exception {
        long id = createQuestion(alice, "[\"tech\"]");
        mvc.perform(put("/api/questions/" + id).with(alice).contentType("application/json").content("{\"title\":\"t2\",\"body\":\"b2\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tags", contains("tech")));
        mvc.perform(put("/api/questions/" + id).with(alice).contentType("application/json")
                .content("{\"title\":\"t2\",\"body\":\"b2\",\"tags\":[\"code\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tags", contains("code")));
        assertThat(dbTags(id)).containsExactly("code");
    }

    @Test
    void aQuestionCanNeverBeLeftWithoutTags_orWithAnUnknownOne() throws Exception {
        long id = createQuestion(alice, "[\"tech\"]");
        mvc.perform(put("/api/questions/" + id + "/tags").with(alice).contentType("application/json").content("{\"tags\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/questions/" + id + "/tags").with(alice).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/questions/" + id + "/tags").with(alice).contentType("application/json").content("{\"tags\":[\"nope\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/questions/" + id).with(alice).contentType("application/json")
                .content("{\"title\":\"t\",\"body\":\"b\",\"tags\":[]}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/questions/" + id).with(alice).contentType("application/json")
                .content("{\"title\":\"t\",\"body\":\"b\",\"tags\":[\"nope\"]}")).andExpect(status().isBadRequest());
        assertThat(dbTags(id)).containsExactly("tech");                                   // unchanged by every rejected attempt
    }

    @Test
    void onlyTheOwnerCanChangeTags() throws Exception {
        long id = createQuestion(alice, "[\"tech\"]");
        mvc.perform(put("/api/questions/" + id + "/tags").with(bob).contentType("application/json").content("{\"tags\":[\"math\"]}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/questions/" + id).with(bob).contentType("application/json")
                .content("{\"title\":\"t\",\"body\":\"b\",\"tags\":[\"math\"]}")).andExpect(status().isForbidden());
        assertThat(dbTags(id)).containsExactly("tech");
        mvc.perform(get("/api/questions/999999/tags").with(alice)).andExpect(status().isNotFound());
    }

    @Test
    void userCannotCreateTags_theEndpointIsGone() throws Exception {
        mvc.perform(post("/api/tags").with(alice).contentType("application/json").content("{\"name\":\"networking\"}"))
                .andExpect(status().isMethodNotAllowed());
        em.flush();
        assertThat(jdbc.queryForObject("select count(*) from tags", Integer.class)).isEqualTo(4);
    }
}
