package com.example.mcp;

import com.example.mcp.service.McpServerService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
public class RestMcpServerApplication {

    @Autowired
    private McpServerService mcpServerService;

    public static void main(String[] args) {
        SpringApplication.run(RestMcpServerApplication.class, args);
    }

    @PostConstruct
    public void init() throws Exception {

        try {
            mcpServerService.initTools();
        } catch (Exception e) {
            System.out.println("Error during MCP server initialization" + ": " + e.getMessage());
        }


    }


}