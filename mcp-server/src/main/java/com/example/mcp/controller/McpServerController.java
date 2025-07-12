
package com.example.mcp.controller;

import com.example.mcp.service.McpServerService;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class McpServerController {

    @Autowired
    private McpServerService mcpServerService;

    @GetMapping("/tools")
    public ResponseEntity<List<Tool>> getTools() {
        return ResponseEntity.ok(mcpServerService.getTools());
    }

    @GetMapping("/tools/{id}")
    public ResponseEntity<Tool> getTool(@PathVariable String id) {
        Optional<Tool> o1 = mcpServerService.getTools().stream().filter(tool -> tool.name().equals(id)).findFirst();
        return o1.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/tools/{id}")
    public ResponseEntity<String> deleteTool(@PathVariable String id) {
        try {
            mcpServerService.deleteTool(id);
            return ResponseEntity.ok("Tool " + id + " deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to delete tool: " + e.getMessage());
        }
    }

    @PostMapping("/register-openapi")
    public ResponseEntity<String> registerOpenApi(@RequestBody OpenApiRegistrationRequest request) {
        try {
            mcpServerService.registerOpenApi(request);
            return ResponseEntity.ok("Tool registered successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to register tool: " + e.getMessage());
        }
    }

    @PostMapping("/update-tool-description")
    public ResponseEntity<String> updateToolDescription(@RequestBody UpdateToolDescriptionRequest request) {
        try {
            mcpServerService.updateToolDescription(request.getToolName(), request.getNewDescription());
            return ResponseEntity.ok("Tool description updated successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to update tool description: " + e.getMessage());
        }
    }

    public static class OpenApiRegistrationRequest {
        private String type;
        private String source;
        private String friendlyName;
        private String description;

        // Getters and setters

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getFriendlyName() {
            return friendlyName;
        }

        public void setFriendlyName(String friendlyName) {
            this.friendlyName = friendlyName;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public static class UpdateToolDescriptionRequest {
        private String toolName;
        private String newDescription;

        public String getToolName() {
            return toolName;
        }

        public void setToolName(String toolName) {
            this.toolName = toolName;
        }

        public String getNewDescription() {
            return newDescription;
        }

        public void setNewDescription(String newDescription) {
            this.newDescription = newDescription;
        }
    }
}
