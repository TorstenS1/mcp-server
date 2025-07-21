package de.augmentia.example.mcp.sampletools;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * UserController provides sample implementation of OpenApi Rest interface.
 *
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    /**
     * GET /api/v1/users : Get All Users
     * Retrieves a paginated list of users, allowing for optional filtering and sorting.
     */
    @GetMapping
    public ResponseEntity<String> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false, name = "filter[status]") String statusFilter,
            @RequestParam(required = false, name = "filter[email]") String emailFilter) {

        String[] result = new String[1];
        String jsonResponse = "{ \"message\": \"Found users: "+ "\" \n" +
                "  \"id\": \"a1b2c3d4-e5f6-7890-1234-567890abcdef\",\n" +
                "  \"firstName\": \"Anna\",\n" +
                "  \"lastName\": \"Musterfrau\",\n" +
                "  \"email\": \"anna.musterfrau@example.com\",\n" +
                "  \"username\": \"anna.m\",\n" +
                "  \"status\": \"ACTIVE\",\n" +
                "  \"createdAt\": \"2024-01-15T09:00:00Z\",\n" +
                "  \"updatedAt\": \"2025-07-05T07:38:57Z\"\n" +
                "}\n]\n";

        return ResponseEntity.ok(jsonResponse);
    }

    /**
     * POST /api/v1/users : Create New User
     * Creates a new user account.
     */
    @PostMapping
    public ResponseEntity<String> createUser() {
        return ResponseEntity.notFound().build();
    }

    /**
     * GET /api/v1/users/{userId} : Get User by ID
     * Retrieves a specific user by their unique ID.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<String> getUserById(@PathVariable String userId) {
        String jsonResponse = "{ \"message\": \"User details for userId: " + userId + "\"+\n" +
                "  \"id\": \"a1b2c3d4-e5f6-7890-1234-567890abcdef\",\n" +
                "  \"firstName\": \"Anna\",\n" +
                "  \"lastName\": \"Musterfrau\",\n" +
                "  \"email\": \"anna.musterfrau@example.com\",\n" +
                "  \"username\": \"anna.m\",\n" +
                "  \"status\": \"ACTIVE\",\n" +
                "  \"createdAt\": \"2024-01-15T09:00:00Z\",\n" +
                "  \"updatedAt\": \"2025-07-05T07:38:57Z\"\n" +
                "}\n";

        return ResponseEntity.ok(jsonResponse);
    }

    /**
     * PUT /api/v1/users/{userId} : Update User (Full Replacement)
     * Fully replaces an existing user resource with the provided data.
     */
    @PutMapping("/{userId}")
    public ResponseEntity<String> updateUser(@PathVariable String userId, @RequestBody String userData) {
        return ResponseEntity.ok().body(userData);
    }

    /**
     * PATCH /api/v1/users/{userId} : Partially Update User
     * Partially updates an existing user resource with only the fields provided.
     */
    @PatchMapping("/{userId}")
    public ResponseEntity<String> patchUser(@PathVariable String userId) {
        return ResponseEntity.notFound().build();
    }

    /**
     * DELETE /api/v1/users/{userId} : Delete User
     * Deletes a user account.
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId) {
        return ResponseEntity.notFound().build();
    }
}