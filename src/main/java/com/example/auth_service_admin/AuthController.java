package com.example.auth_service_admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;

@RestController
@CrossOrigin(
        origins = {"https://admin.our-galaxy.space", "http://localhost:5173"},
        allowCredentials = "true",
        maxAge = 3600,
        allowedHeaders = "*",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS}
)
public class AuthController {

    @Value("${keycloak.url}")
    private String keycloakUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/auth/callback")
    public ResponseEntity<?> handleCallback(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        String codeVerifier = body.get("codeVerifier");
        String redirectUri = body.get("redirectUri");

        String tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", clientId);
        formData.add("code", code);
        formData.add("redirect_uri", redirectUri);
        formData.add("code_verifier", codeVerifier);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    tokenUrl, HttpMethod.POST, request, JsonNode.class
            );

            JsonNode tokenJson = response.getBody();
            if (tokenJson == null || !tokenJson.has("access_token")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid token response"));
            }

            String accessToken = tokenJson.get("access_token").asText();
            JsonNode userInfo = getUserInfo(accessToken);

            Map<String, Object> result = Map.of(
                    "access_token", accessToken,
                    "refresh_token", tokenJson.get("refresh_token").asText(),
                    "id_token", tokenJson.get("id_token").asText(),
                    "expires_in", tokenJson.get("expires_in").asInt(),
                    "userInfo", userInfo
            );

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Token exchange failed: " + e.getMessage()));
        }
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        String tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("client_id", clientId);
        formData.add("refresh_token", refreshToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    tokenUrl, HttpMethod.POST, request, JsonNode.class
            );

            JsonNode tokenJson = response.getBody();
            if (tokenJson == null || !tokenJson.has("access_token")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid refresh response"));
            }

            String accessToken = tokenJson.get("access_token").asText();
            JsonNode userInfo = getUserInfo(accessToken);

            Map<String, Object> result = Map.of(
                    "access_token", accessToken,
                    "refresh_token", tokenJson.get("refresh_token").asText(),
                    "expires_in", tokenJson.get("expires_in").asInt(),
                    "userInfo", userInfo
            );

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Refresh failed: " + e.getMessage()));
        }
    }

    private JsonNode getUserInfo(String accessToken) {
        String userInfoUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/userinfo";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    userInfoUrl, HttpMethod.GET, entity, JsonNode.class
            );
            return response.getBody();
        } catch (Exception e) {
            return objectMapper.createObjectNode().put("error", "Failed to load user info");
        }
    }
}
