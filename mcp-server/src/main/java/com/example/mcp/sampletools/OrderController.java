package com.example.mcp.sampletools;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;


import java.net.URI;
import java.util.Optional;
import java.util.UUID;

/**
 * OrderController provides sample implementation of OpenApi Rest interface.
 * It allows users to retrieve order details by order ID.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    /**
     * GET /api/v1/orders/{orderId} : Get Order Details
     * Retrieves the details of a specific order.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<String> getOrderDetails(@PathVariable UUID orderId) {
        String result = "{ \"message\": \"Order details for orderId: " + orderId + "\" }";
        result.concat("{\n" +
                "  \"orderId\": \"5c1f0d3a-1b2c-4e5f-8a9b-0c1d2e3f4a5b\",\n" +
                "  \"userId\": \"a1b2c3d4-e5f6-7890-1234-567890abcdef\",\n" +
                "  \"status\": \"PENDING\",\n" +
                "  \"totalAmount\": 79.97,\n" +
                "  \"createdAt\": \"2025-07-04T18:00:00Z\",\n" +
                "  \"updatedAt\": \"2025-07-05T07:54:23Z\",\n" +
                "  \"items\": [\n" +
                "    {\n" +
                "      \"productId\": \"p9o8i7u6-y5t4-3r2q-1w0e-zxcvbnmlkjhg\",\n" +
                "      \"quantity\": 1,\n" +
                "      \"pricePerUnit\": 49.99\n" +
                "    },\n" +
                "    {\n" +
                "      \"productId\": \"a1s2d3f4-g5h6-7j8k-9l0p-zxcvbnmlkjhg\",\n" +
                "      \"quantity\": 2,\n" +
                "      \"pricePerUnit\": 14.99\n" +
                "    }\n" +
                "  ],\n" +
                "  \"shippingAddress\": {\n" +
                "    \"street\": \"Musterweg 7\",\n" +
                "    \"city\": \"Hamburg\",\n" +
                "    \"zipCode\": \"22123\",\n" +
                "    \"country\": \"Germany\"\n" +
                "  },\n" +
                "  \"trackingNumber\": null\n" +
                "}");

        return ResponseEntity.ok(result);
    }

}