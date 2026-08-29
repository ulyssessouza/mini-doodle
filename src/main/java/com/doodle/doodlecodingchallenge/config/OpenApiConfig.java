package com.doodle.doodlecodingchallenge.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI doodleApi() {
        return new OpenAPI().info(new Info()
            .title("Mini Doodle API")
            .version("v1")
            .description("Meeting scheduling service: time slots, meetings, free/busy calendar views"));
    }
}
