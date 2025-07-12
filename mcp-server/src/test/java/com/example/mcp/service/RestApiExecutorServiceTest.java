package com.example.mcp.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RestApiExecutorServiceTest {

    @Test
    void testInitialize() {
        RestApiExecutorService restApiExecutorService = new RestApiExecutorService();
        String result = restApiExecutorService.initialize(null, null);
        assertNotNull(result);
    }

    @Test
    void testExecuteApiCall() {
        RestApiExecutorService restApiExecutorService = new RestApiExecutorService();
        try {
            Object result = restApiExecutorService.executeApiCall("", "", null);
            assertNotNull(result);
        } catch (IOException e) {
            // ignore
        }
    }
}