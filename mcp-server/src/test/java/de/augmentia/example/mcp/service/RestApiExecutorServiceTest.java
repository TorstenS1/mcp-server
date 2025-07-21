package de.augmentia.example.mcp.service;

import de.augmentia.example.mcp.config.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

public class RestApiExecutorServiceTest {

    @InjectMocks
    private RestApiExecutorService restApiExecutorService;

    @Mock
    private SecurityProperties securityProperties;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(securityProperties.getAllowedDomains()).thenReturn(Collections.singletonList("api.yourdomain.com"));
    }

    @Test
    void testInitialize() {
        String result = null;
        try {
            result = restApiExecutorService.initialize(new ClassPathResource("users-api.yml").getInputStream(), null);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        assertNotNull(result);
    }

    @Test
    void testExecuteApiCall() {
        try {
            Object result = restApiExecutorService.executeApiCall("", "", null);
            assertNotNull(result);
        } catch (Exception e) {
            // ignore
        }
    }
}