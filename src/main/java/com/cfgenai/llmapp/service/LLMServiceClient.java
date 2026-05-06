package com.cfgenai.llmapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class LLMServiceClient {
    
    private static final Logger logger = LoggerFactory.getLogger(LLMServiceClient.class);
    
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final CloudFoundryServiceConfig serviceConfig;
    
    private String apiKey;
    private String baseUrl;
    private String modelName;
    private boolean isServiceBound;

    public LLMServiceClient(CloudFoundryServiceConfig serviceConfig) {
        this.objectMapper = new ObjectMapper();
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024)) // 1MB buffer
                .build();
        this.serviceConfig = serviceConfig;
        this.modelName = "gpt-4o"; // Updated to latest default model
        
        configureService();
    }
    
    private void configureService() {
        if (serviceConfig.hasServiceBinding()) {
            configureFromServiceBinding();
        } else {
            configureFromEnvironment();
        }
    }
    
    private void configureFromServiceBinding() {
        logger.info("Configuring LLM client from Cloud Foundry service binding");
        
        // Log available credential field names for debugging (without values)
        Map<String, Object> allCreds = serviceConfig.getServiceCredentials();
        if (allCreds != null) {
            logger.info("Available service credential fields: [{}]", String.join(", ", allCreds.keySet()));
        }
        
        // Extract API key from various possible field names
        this.apiKey = serviceConfig.getCredentialValue("api_key", "apikey", "key", "token", "access_token", "auth_token");
        logger.info("API key found: {}", this.apiKey != null ? "[PRESENT]" : "[NOT FOUND]");
        
        // Extract base URL - try many variations including api_base which is what this service uses
        this.baseUrl = serviceConfig.getCredentialValue("api_base", "url", "endpoint", "base_url", "api_url", "host", "server_url", "service_url", "inference_endpoint", "openai_api_base");
        logger.info("Base URL found: {}", this.baseUrl != null ? maskSensitiveUrl(this.baseUrl) : "[NOT FOUND]");
        
        // Extract model name
        String modelFromService = serviceConfig.getCredentialValue("model", "model_name", "deployment_name", "model_id");
        if (modelFromService != null && !modelFromService.isEmpty()) {
            this.modelName = modelFromService;
        }
        logger.info("Model name found: {}", this.modelName);
        
        this.isServiceBound = true;
        logger.info("Configured from service binding - URL: {}, Model: {}, API Key: {}", 
                   maskSensitiveUrl(baseUrl), modelName, apiKey != null ? "[PRESENT]" : "[MISSING]");
    }
    
    private void configureFromEnvironment() {
        logger.info("Configuring LLM client from environment variables");
        
        this.apiKey = Optional.ofNullable(System.getenv("OPENAI_API_KEY"))
                             .orElse(System.getenv("LLM_API_KEY"));
        
        this.baseUrl = Optional.ofNullable(System.getenv("LLM_BASE_URL"))
                              .orElse("https://api.openai.com/v1");
        
        this.modelName = Optional.ofNullable(System.getenv("LLM_MODEL"))
                                .orElse(this.modelName);
        
        logger.info("Configured from environment - URL: {}, Model: {}", maskSensitiveUrl(baseUrl), modelName);
    }
    
    
    public LLMExecutionResult executePrompt(String prompt, int maxTokens, double temperature) {
        if (apiKey == null || apiKey.isEmpty()) {
            return LLMExecutionResult.error("No API key configured. Please check your service binding or environment variables.");
        }
        
        if (baseUrl == null || baseUrl.isEmpty()) {
            return LLMExecutionResult.error("No base URL configured. Please check your service binding or environment variables.");
        }
        
        try {
            String endpoint = determineEndpoint();
            Map<String, Object> payload = createPayload(prompt, maxTokens, temperature);
            
            logger.info("Making request to: {}", maskSensitiveUrl(endpoint));
            
            // Use WebClient for modern reactive HTTP calls with timeout
            Mono<String> responseMono = webClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(30)); // 30 second timeout
            
            // Block for synchronous execution (since this is a batch app)
            String responseBody = responseMono.block();
            
            return parseSuccessfulResponse(responseBody);
            
        } catch (WebClientResponseException e) {
            String errorMsg = String.format("HTTP %d: %s", e.getStatusCode().value(), e.getResponseBodyAsString());
            logger.error("LLM API error: {}", errorMsg);
            return LLMExecutionResult.error(errorMsg);
        } catch (Exception e) {
            String errorMsg = "Unexpected error: " + e.getMessage();
            logger.error(errorMsg, e);
            return LLMExecutionResult.error(errorMsg);
        }
    }
    
    private String determineEndpoint() {
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        
        if (baseUrl.toLowerCase().contains("openai") || baseUrl.toLowerCase().contains("azure")) {
            return normalizedBaseUrl + "/chat/completions";
        } else {
            // Generic LLM service format
            return normalizedBaseUrl + "/v1/completions";
        }
    }
    
    private Map<String, Object> createPayload(String prompt, int maxTokens, double temperature) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", modelName);
        payload.put("max_tokens", maxTokens);
        payload.put("temperature", temperature);
        
        if (baseUrl.toLowerCase().contains("openai") || baseUrl.toLowerCase().contains("azure")) {
            // Chat completions format
            Map<String, String> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);
            payload.put("messages", new Map[]{message});
        } else {
            // Generic completions format
            payload.put("prompt", prompt);
        }
        
        return payload;
    }
    
    private LLMExecutionResult parseSuccessfulResponse(String responseBody) {
        try {
            JsonNode result = objectMapper.readTree(responseBody);
            
            if (!result.has("choices") || result.get("choices").size() == 0) {
                return LLMExecutionResult.error("Unexpected response format: " + responseBody);
            }
            
            JsonNode choice = result.get("choices").get(0);
            String content;
            
            if (choice.has("message")) {
                // Chat completions format
                content = choice.get("message").get("content").asText();
            } else {
                // Completions format
                content = choice.get("text").asText();
            }
            
            // Extract usage information
            Map<String, Object> usage = new HashMap<>();
            if (result.has("usage")) {
                JsonNode usageNode = result.get("usage");
                usage.put("total_tokens", usageNode.has("total_tokens") ? usageNode.get("total_tokens").asInt() : 0);
                usage.put("prompt_tokens", usageNode.has("prompt_tokens") ? usageNode.get("prompt_tokens").asInt() : 0);
                usage.put("completion_tokens", usageNode.has("completion_tokens") ? usageNode.get("completion_tokens").asInt() : 0);
            }
            
            return LLMExecutionResult.success(content.trim(), modelName, usage);
            
        } catch (Exception e) {
            return LLMExecutionResult.error("Failed to parse response: " + e.getMessage());
        }
    }
    
    // Getters for configuration information
    public String getModelName() {
        return modelName;
    }
    
    public String getBaseUrl() {
        return baseUrl;
    }
    
    public boolean isServiceBound() {
        return isServiceBound;
    }
    
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isEmpty();
    }
    
    /**
     * Get a masked version of the base URL for safe logging
     * @return masked URL with sensitive information redacted
     */
    public String getMaskedBaseUrl() {
        return maskSensitiveUrl(baseUrl);
    }
    
    /**
     * Mask sensitive information in URLs (API keys in query params, tokens, etc.)
     * @param url the URL to mask
     * @return masked URL safe for logging
     */
    private String maskSensitiveUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        
        try {
            // Check if URL contains query parameters that might have sensitive data
            if (url.contains("?")) {
                String[] parts = url.split("\\?", 2);
                String baseUrl = parts[0];
                String queryString = parts[1];
                
                // Mask common sensitive query parameters
                String maskedQuery = queryString
                    .replaceAll("(?i)(api[_-]?key|token|auth|secret|password|credential)=[^&]*", "$1=***")
                    .replaceAll("(?i)key=[^&]*", "key=***");
                
                return baseUrl + "?" + maskedQuery;
            }
            
            // Mask potential API keys in the path (common pattern: /api/v1/key/actual-key/)
            return url.replaceAll("(?i)(/api/[^/]*/(?:key|token|auth)[/])[^/]+(/.*)?", "$1***$2");
            
        } catch (Exception e) {
            // If URL parsing fails, return a generic masked version
            return url.replaceAll("(https?://[^/]+).*", "$1/***");
        }
    }
}