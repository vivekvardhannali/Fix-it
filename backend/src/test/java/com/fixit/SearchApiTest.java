package com.fixit;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.fixit.entity.Question;
import com.fixit.entity.QuestionTag;
import com.fixit.entity.User;
import com.fixit.repository.QuestionRepository;
import com.fixit.repository.QuestionTagRepository;
import com.fixit.repository.UserRepository;
import com.fixit.search.QuestionEmbeddingService;
import com.fixit.service.TagService;
import com.fixit.support.FakeEmbeddings;

import jakarta.persistence.EntityManager;

/** Checkpoint 19: the search endpoint, end to end over HTTP (fake provider; test thresholds/weights). */
@SpringBootTest(properties = { FakeEmbeddings.P_PROVIDER, FakeEmbeddings.P_MODEL, FakeEmbeddings.P_URL,
        FakeEmbeddings.P_KEY, FakeEmbeddings.P_DIM, FakeEmbeddings.P_THRESHOLD, FakeEmbeddings.P_LEXICAL_THRESHOLD,
        FakeEmbeddings.P_HYBRID_THRESHOLD, FakeEmbeddings.P_WEIGHT })
@Import(FakeEmbeddings.Config.class)
@AutoConfigureMockMvc
@Transactional
class SearchApiTest {

    static final String QUERY = "wifi disconnecting hostel laptop";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired QuestionRepository questions;
    @Autowired QuestionTagRepository questionTags;
    @Autowired TagService tagService;
    @Autowired QuestionEmbeddingService embeddingService;
    @Autowired FakeEmbeddings fake;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    RequestPostProcessor bob;
    Question q1, q2, q3, q4;

    @BeforeEach
    void setUp() {
        fake.reset();
        User alice = users.save(new User("alice@smail.iitm.ac.in"));
        bob = authentication(TestAuth.as(users.save(new User("bob@smail.iitm.ac.in"))));
        q1 = make(alice, "WiFi keeps disconnecting in hostel", "my laptop loses wifi connection every hour in hostel block", "tech");
        q2 = make(alice, "Cannot connect to hostel WiFi", "laptop wifi disconnects hostel connection", "tech", "others");
        q3 = make(alice, "How to register for electives", "course registration electives deadline portal", "math");
        q4 = make(alice, "Mess food quality complaint", "dinner served cold and tasteless", "code");
        em.flush();
        em.clear();   // tags were added through the repository, so reload questions fresh like a real request would
    }

    private Question make(User author, String title, String body, String... tagNames) {
        Question q = questions.save(new Question(author, title, body));
        for (String t : tagNames) {
            questionTags.save(new QuestionTag(q, tagService.findOrCreate(t)));
        }
        em.flush();
        embeddingService.refresh(q.getId(), title, body);
        return q;
    }

    @Test
    void returnsRelevantQuestionsInDecreasingRelevance_withTheFieldsTheResultListNeeds() throws Exception {
        mvc.perform(get("/api/search").param("q", QUERY).with(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].questionId").value(q1.getId()))
                .andExpect(jsonPath("$[1].questionId").value(q2.getId()))
                .andExpect(jsonPath("$[0].title").value("WiFi keeps disconnecting in hostel"))
                .andExpect(jsonPath("$[0].bodyPreview").value("my laptop loses wifi connection every hour in hostel block"))
                .andExpect(jsonPath("$[0].tags", contains("tech")))
                .andExpect(jsonPath("$[0].isResolved").value(false))
                .andExpect(jsonPath("$[0].interestCount").value(0))
                .andExpect(jsonPath("$[0].relevance").isNumber())
                .andExpect(jsonPath("$[0].semanticScore").isNumber())
                .andExpect(jsonPath("$[0].lexicalScore").isNumber())
                .andExpect(jsonPath("$[1].tags", contains("others", "tech")))
                .andExpect(content().string(not(containsString("authorId"))));
    }

    @Test
    void tagFilter_repeatedParameter_isAUnion_andNoTagsMeansEverything() throws Exception {
        mvc.perform(get("/api/search").param("q", QUERY).param("tags", "others").with(bob))
                .andExpect(jsonPath("$[*].questionId", contains(q2.getId().intValue())));
        mvc.perform(get("/api/search").param("q", QUERY).param("tags", "math").param("tags", "others").with(bob))
                .andExpect(jsonPath("$[*].questionId", contains(q2.getId().intValue())));
        mvc.perform(get("/api/search").param("q", QUERY).param("tags", "math").with(bob))
                .andExpect(jsonPath("$", empty()));
        mvc.perform(get("/api/search").param("q", QUERY).param("tags", "TECH").with(bob))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void tagsMustBeFromTheFixedSet_andACommaIsNotASeparator() throws Exception {
        mvc.perform(get("/api/search").param("q", QUERY).param("tags", "networking").with(bob))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Choose from: tech, math, code, others")));
        // "tech,math" is ONE parameter value (read raw, not split) -> not a tag name -> rejected
        mvc.perform(get("/api/search").param("q", QUERY).param("tags", "tech,math").with(bob))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/search").param("q", QUERY).param("tags", "tech").param("tags", "math").with(bob))
                .andExpect(status().isOk());
    }

    @Test
    void noSuitableMatchReturnsAnEmptyList() throws Exception {
        mvc.perform(get("/api/search").param("q", "quantum chromodynamics lagrangian").with(bob))
                .andExpect(status().isOk()).andExpect(jsonPath("$", empty()));
    }

    @Test
    void modesCanBeComparedWhileTuning() throws Exception {
        mvc.perform(get("/api/search").param("q", QUERY).param("mode", "lexical").with(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].semanticScore").doesNotExist())
                .andExpect(jsonPath("$[0].lexicalScore").isNumber());
        mvc.perform(get("/api/search").param("q", QUERY).param("mode", "SEMANTIC").with(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].semanticScore").isNumber())
                .andExpect(jsonPath("$[0].lexicalScore").doesNotExist());
        mvc.perform(get("/api/search").param("q", QUERY).param("mode", "hybrid").with(bob))
                .andExpect(jsonPath("$[0].semanticScore").isNumber()).andExpect(jsonPath("$[0].lexicalScore").isNumber());
    }

    @Test
    void invalidRequestsAre400() throws Exception {
        mvc.perform(get("/api/search").with(bob)).andExpect(status().isBadRequest());                          // q missing
        mvc.perform(get("/api/search").param("q", "  ").with(bob)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/search").param("q", "x".repeat(501)).with(bob)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/search").param("q", QUERY).param("mode", "magic").with(bob)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/search").param("q", QUERY).param("tags", " ").with(bob)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/search").param("q", QUERY).param("tags", "x".repeat(51)).with(bob)).andExpect(status().isBadRequest());
        String[] eleven = { "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11" };
        mvc.perform(get("/api/search").param("q", QUERY).param("tags", eleven).with(bob)).andExpect(status().isBadRequest());
    }

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(get("/api/search").param("q", QUERY)).andExpect(status().isUnauthorized());
    }

    @Test
    void embeddingProviderOutageIs503WithoutInternalDetail() throws Exception {
        fake.failing = true;
        mvc.perform(get("/api/search").param("q", QUERY).with(bob))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Search is temporarily unavailable"))
                .andExpect(content().string(not(containsString("fake provider"))));
        mvc.perform(get("/api/search").param("q", QUERY).param("mode", "lexical").with(bob))   // lexical unaffected
                .andExpect(status().isOk());
    }
}
