package com.example.mcp.sampletools;

import com.example.mcp.service.RestApiExecutorService;
import lombok.extern.slf4j.Slf4j;

import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for handling file reading and API calls in the MCP Tool.
 * Provides endpoints to read files from the server and execute API calls.
 * For later use, this controller can be extended to include more tools and functionalities.
 */
@RestController
@RequestMapping("/api/v1")
@Slf4j
public class MCPToolController {

    @Value("${config.directory:}")
    private String configFilePath;

    @Autowired
    private RestApiExecutorService restApiExecutorService;

    private final Tika tika = new Tika(); // For content type detection

    /**
     * Reads a file from the server's file system and returns it as a downloadable resource.
     *
     * @param filename The name of the file to retrieve.
     * @return ResponseEntity containing the file resource and appropriate headers.
     */
    //TODO deactivate this endpoint for security reasons
    //@GetMapping("/files/{filename}")
    public ResponseEntity<Resource> readFile(@PathVariable String filename) {
        try {
            // 1. Sanitize filename to prevent directory traversal attacks
            // Normalize the path and resolve it against the base directory
            Path fileBasePath = Paths.get(configFilePath + "/data/").toAbsolutePath().normalize();
            Path filePath = fileBasePath.resolve(filename).normalize();

            // Ensure the resolved path is still within the base directory
            if (!filePath.startsWith(fileBasePath)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path.");
            }

            // 2. Check if the file exists and is readable
            if (!Files.exists(filePath) || !Files.isReadable(filePath) || Files.isDirectory(filePath)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File '" + filename + "' not found or not accessible.");
            }

            // 3. Load the file as a Spring Resource
            Resource resource = new UrlResource(filePath.toUri());

            // 4. Determine content type (MIME type)
            String contentType;
            try {
                // Option A: Using Files.probeContentType (JDK built-in, less robust)
                contentType = Files.probeContentType(filePath);
                if (contentType == null) {
                    // Fallback to Tika if JDK's probe fails or is too generic
                    contentType = tika.detect(filePath.toFile());
                }
            } catch (IOException e) {
                // Log error but proceed with generic type
                System.err.println("Could not determine file content type for " + filename + ": " + e.getMessage());
                contentType = "application/octet-stream"; // Default generic binary type
            }

            if (contentType == null) {
                contentType = "application/octet-stream"; // Final fallback
            }

            // 5. Build and return the ResponseEntity
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    // Use "inline" to display in browser if possible, or "attachment" to force download
                    .body(resource);

        } catch (MalformedURLException e) {
            // This can happen if the file path is invalid for a URL
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error with file URL: " + e.getMessage());
        } catch (ResponseStatusException e) {
            // Re-throw already handled HTTP errors
            throw e;
        } catch (Exception e) {
            // Catch any other unexpected exceptions
            System.err.println("An error occurred while serving file " + filename + ": " + e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred while reading the file.");
        }
    }

    @PostMapping("/apiCall")
    public ResponseEntity<String> apiCall(@RequestBody Map<String, Object> requestBody) {
        String toolName = requestBody.get("toolName").toString();
        String result = restApiExecutorService.executeApiCall("http://localhost:8110/api/v1", toolName, requestBody);
        return ResponseEntity.ok(result);
    }

}
