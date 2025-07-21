
package de.augmentia.example.mcp.controller;

import de.augmentia.example.mcp.exception.ToolNotFoundException;
import de.augmentia.example.mcp.service.McpServerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(McpServerController.class)
public class McpServerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private McpServerService mcpServerService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void getTools() throws Exception {
        when(mcpServerService.getTools()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/tools"))
                .andExpect(status().isOk());
    }

    @Test
    public void getTool_whenToolExists() throws Exception {
        McpSchema.Tool tool = new McpSchema.Tool("test", "test description", (McpSchema.JsonSchema) null);
        when(mcpServerService.getTools()).thenReturn(Collections.singletonList(tool));
        mockMvc.perform(get("/api/tools/test"))
                .andExpect(status().isOk());
    }

    @Test
    public void getTool_whenToolDoesNotExist() throws Exception {
        when(mcpServerService.getTools()).thenThrow(new ToolNotFoundException("Tool not found"));
        mockMvc.perform(get("/api/tools/test"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void deleteTool() throws Exception {
        doNothing().when(mcpServerService).deleteTool("test");
        mockMvc.perform(delete("/api/tools/test"))
                .andExpect(status().isOk());
    }

    @Test
    public void deleteTool_whenToolDoesNotExist() throws Exception {
        doThrow(new ToolNotFoundException("Tool not found")).when(mcpServerService).deleteTool("test");
        mockMvc.perform(delete("/api/tools/test"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void registerOpenApi() throws Exception {
        McpServerController.OpenApiRegistrationRequest request = new McpServerController.OpenApiRegistrationRequest();
        request.setType("openapi");
        request.setSource("test");
        request.setFriendlyName("test");
        request.setDescription("test");

        doNothing().when(mcpServerService).registerOpenApi(any());

        mockMvc.perform(post("/api/register-openapi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    public void updateToolDescription() throws Exception {
        McpServerController.UpdateToolDescriptionRequest request = new McpServerController.UpdateToolDescriptionRequest();
        request.setToolName("test");
        request.setNewDescription("new description");

        doNothing().when(mcpServerService).updateToolDescription("test", "new description");

        mockMvc.perform(post("/api/update-tool-description")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    public void updateToolDescription_whenToolDoesNotExist() throws Exception {
        McpServerController.UpdateToolDescriptionRequest request = new McpServerController.UpdateToolDescriptionRequest();
        request.setToolName("test");
        request.setNewDescription("new description");

        doThrow(new ToolNotFoundException("Tool not found")).when(mcpServerService).updateToolDescription("test", "new description");

        mockMvc.perform(post("/api/update-tool-description")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
