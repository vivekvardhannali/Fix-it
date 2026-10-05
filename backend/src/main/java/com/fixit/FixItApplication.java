package com.fixit;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FixItApplication {

    static {
        // Before anything else (database connections read the default zone when they are opened): always IST,
        // whatever the server's own zone is.
        TimeZone.setDefault(TimeZone.getTimeZone(AppTime.ZONE));
    }

    public static void main(String[] args) {
        SpringApplication.run(FixItApplication.class, args);
    }
}
