package com.example.mcp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**") // Wende CORS auf alle /api-Pfade an
                .allowedOrigins("http://localhost:5173") // Ersetze dies durch den genauen Origin deines Frontends
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*") // Erlaube alle Header
                .allowCredentials(true); // Erlaube Cookies und Authentifizierungs-Header
    }
}
