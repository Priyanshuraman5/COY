package com.coy.discover;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DiscoverApplication {
    public static void main(String[] args) { SpringApplication.run(DiscoverApplication.class, args); }

    @Bean
    WebMvcConfigurer cors() {
        return new WebMvcConfigurer() {
            @Override public void addCorsMappings(CorsRegistry r) {
                r.addMapping("/**").allowedOrigins("http://localhost:5173").allowedHeaders("*").allowedMethods("GET", "POST", "DELETE");
            }
        };
    }
}
