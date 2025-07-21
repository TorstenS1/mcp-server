package de.augmentia.example.mcp.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Data
public class McpServer {

    private String name;

    private String url;

    private String apiDefinition; // Maps to 'api_definition' column

    private Integer port_number;

    private List<OpenApiDef> tools = new ArrayList<>();

    private HashMap<String,String> environmentVariables = new HashMap<>();

    public McpServer() {
        // Default constructor
    }

    public void addTool(OpenApiDef tool) {
        if (tool != null) {
            this.tools.add(tool);
        }
    }

}
