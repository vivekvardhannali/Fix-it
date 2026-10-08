package com.fixit.config;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fixit.AppTime;

@Configuration
public class JsonTimeConfig {

    /**
     * Every LocalDateTime in a response is an IST wall-clock time; write it with its offset
     * (e.g. {@code 2026-10-04T23:19:15.896+05:30}) so it is unambiguous for any client.
     */
    @Bean
    Jackson2ObjectMapperBuilderCustomizer istTimestamps() {
        return builder -> builder.serializerByType(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(value.atZone(AppTime.ZONE).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }
        });
    }
}
