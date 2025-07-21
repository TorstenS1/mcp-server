package de.augmentia.example.mcp.model;

import lombok.Data;

@Data
public class OpenApiDef {

    private String name;

    private String description;

    private String version; // later use

    private String restApiUrl; // Optional REST API URL for the tool

    private String apiDefinition; // Optional API definition for the tool, if applicable

}