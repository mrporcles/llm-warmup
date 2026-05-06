package com.cfgenai.llmapp.health;

import com.cfgenai.llmapp.service.CloudFoundryServiceConfig;
import com.cfgenai.llmapp.service.LLMServiceClient;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("llmService")
public class LLMServiceHealthIndicator implements HealthIndicator {
    
    private final CloudFoundryServiceConfig serviceConfig;
    private final LLMServiceClient llmServiceClient;
    
    public LLMServiceHealthIndicator(CloudFoundryServiceConfig serviceConfig, LLMServiceClient llmServiceClient) {
        this.serviceConfig = serviceConfig;
        this.llmServiceClient = llmServiceClient;
    }
    
    @Override
    public Health health() {
        try {
            boolean hasService = serviceConfig != null && serviceConfig.hasServiceBinding();
            boolean hasApiKey = llmServiceClient != null && llmServiceClient.hasApiKey();
            boolean hasBaseUrl = llmServiceClient != null && llmServiceClient.getBaseUrl() != null && !llmServiceClient.getBaseUrl().isEmpty();
            
            if (hasService && hasApiKey && hasBaseUrl) {
                return Health.up()
                    .withDetail("service", "ai-models service bound")
                    .withDetail("model", llmServiceClient.getModelName())
                    .withDetail("baseUrl", llmServiceClient.getMaskedBaseUrl())
                    .withDetail("hasApiKey", true)
                    .withDetail("status", "LLM service fully configured")
                    .build();
            } else {
                // Still report UP to keep the app running, but indicate configuration status
                return Health.up()
                    .withDetail("service", hasService ? "ai-models service bound" : "No ai-models service bound")
                    .withDetail("hasApiKey", hasApiKey)
                    .withDetail("hasBaseUrl", hasBaseUrl)
                    .withDetail("status", "LLM service configuration incomplete - will use fallback behavior")
                    .withDetail("message", "Application running but LLM functionality may be limited")
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("message", "Health check failed")
                .build();
        }
    }
}