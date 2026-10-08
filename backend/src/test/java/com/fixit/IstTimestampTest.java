package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixit.entity.User;
import com.fixit.repository.UserRepository;

/** The app runs on Indian Standard Time and says so in every timestamp it returns. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IstTimestampTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void theApplicationAndTheDatabaseSessionUseIst() {
        assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Kolkata");
        assertThat(jdbc.queryForObject("show timezone", String.class)).isEqualTo("Asia/Kolkata");
    }

    @Test
    void everyTimestampInAResponseCarriesThePlus0530Offset_andIsTheCurrentTime() throws Exception {
        var alice = authentication(TestAuth.as(users.save(new User("alice@smail.iitm.ac.in"))));
        String body = mvc.perform(post("/api/questions").with(alice).contentType("application/json")
                .content("{\"title\":\"t\",\"body\":\"b\",\"tags\":[\"tech\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode question = mapper.readTree(body);

        for (String field : new String[] { "createdAt", "updatedAt" }) {
            String text = question.get(field).asText();
            assertThat(text).as(field).endsWith("+05:30");
            OffsetDateTime parsed = OffsetDateTime.parse(text);
            assertThat(parsed.getOffset()).isEqualTo(ZoneOffset.ofHoursMinutes(5, 30));
            // it is "now" in absolute terms - proves the wall-clock value really is IST, not just labelled IST
            assertThat(Duration.between(parsed.toInstant(), java.time.Instant.now()).abs()).isLessThan(Duration.ofMinutes(2));
        }
        // same format on a list endpoint
        String list = mvc.perform(get("/api/me/questions").with(alice)).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(list).get("items").get(0).get("createdAt").asText()).endsWith("+05:30");
    }

    @Test
    void errorTimestampsStayUnambiguousToo() throws Exception {
        String body = mvc.perform(get("/api/me")).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(body).get("timestamp").asText()).matches(".*(Z|[+-]\\d\\d:\\d\\d)$");
    }
}
