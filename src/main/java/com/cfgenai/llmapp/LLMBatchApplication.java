package com.cfgenai.llmapp;

import com.cfgenai.llmapp.service.CloudFoundryServiceConfig;
import com.cfgenai.llmapp.service.LLMExecutionResult;
import com.cfgenai.llmapp.service.LLMServiceClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@SpringBootApplication
@EnableScheduling
public class LLMBatchApplication implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(LLMBatchApplication.class);
    private static final DateTimeFormatter LOG_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final LLMServiceClient llmServiceClient;
    private final CloudFoundryServiceConfig serviceConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public LLMBatchApplication(LLMServiceClient llmServiceClient, CloudFoundryServiceConfig serviceConfig) {
        this.llmServiceClient = llmServiceClient;
        this.serviceConfig = serviceConfig;
    }

    public static void main(String[] args) {
        // Disable Spring Boot banner and reduce startup noise
        System.setProperty("spring.main.banner-mode", "off");
        System.setProperty("logging.level.org.springframework", "WARN");
        System.setProperty("logging.level.org.apache.catalina", "WARN");
        System.setProperty("logging.level.org.hibernate", "WARN");
        
        // Keep the application running - don't close the context for scheduled execution
        SpringApplication.run(LLMBatchApplication.class, args);
    }
    
    @Override
    public void run(String... args) {
        logger.info("🚀 Starting LLM Warmup Application with scheduled execution");
        
        try {
            // Run the prompt once at startup
            executeSinglePrompt();
            logger.info("✅ Initial execution completed. Application will continue running for scheduled executions.");
        } catch (Exception e) {
            logger.error("❌ Error during initial execution: {}", e.getMessage(), e);
            logger.info("Application will continue running and retry on scheduled executions.");
        }
        
        logger.info("Execution interval: {} (configured via EXECUTION_INTERVAL_SECONDS environment variable)", 
                   getExecutionIntervalDescription());
        logger.info("📊 Health check available at: /actuator/health");
        logger.info("🔄 Application is now running continuously...");
    }
    
    @Scheduled(fixedDelayString = "${app.execution.interval:300000}") // Default 5 minutes (300000ms)
    public void scheduledExecution() {
        try {
            logger.info("⏰ Scheduled execution starting...");
            executeSinglePrompt();
            logger.info("⏰ Scheduled execution completed. Next execution in {} seconds.", 
                       getExecutionInterval() / 1000);
        } catch (Exception e) {
            logger.error("❌ Error during scheduled execution: {}", e.getMessage(), e);
            logger.info("Application will continue running and retry on next scheduled execution.");
        }
    }
    
    private int getExecutionInterval() {
        String intervalStr = System.getenv("EXECUTION_INTERVAL_SECONDS");
        if (intervalStr != null) {
            try {
                return Integer.parseInt(intervalStr) * 1000; // Convert to milliseconds
            } catch (NumberFormatException e) {
                logger.warn("Invalid EXECUTION_INTERVAL_SECONDS value '{}', using default 5 minutes", intervalStr);
            }
        }
        return 300000; // Default 5 minutes in milliseconds
    }
    
    private String getExecutionIntervalDescription() {
        int intervalMs = getExecutionInterval();
        int seconds = intervalMs / 1000;
        
        if (seconds >= 3600) {
            return String.format("%.1f hour(s)", seconds / 3600.0);
        } else if (seconds >= 60) {
            return String.format("%.1f minute(s)", seconds / 60.0);
        } else {
            return String.format("%d second(s)", seconds);
        }
    }
    
    private int executeSinglePrompt() {
        try {
            logSeparator("=", 80);
            logger.info("LLM PROMPT EXECUTION STARTING");
            logSeparator("=", 80);
        
        logger.info("Initializing LLM service client...");
        
        // Log configuration summary
        logConfigurationSummary();
        
        // Log Cloud Foundry environment info
        logCloudFoundryInfo();
        
        // Get execution parameters
        String prompt = getDefaultPrompt();
        double temperature = getTemperature();
        int maxTokens = getMaxTokens();
        
        logger.info("Execution Parameters:");
        logger.info("  Prompt: {}", prompt);
        logger.info("  Temperature: {}", temperature);
        logger.info("  Max Tokens: {}", maxTokens);
        
        logSeparator("-", 80);
        logger.info("EXECUTING LLM PROMPT...");
        logSeparator("-", 80);
        
        // Execute the prompt
        LocalDateTime startTime = LocalDateTime.now();
        logger.info("Execution started at: {}", startTime.format(LOG_TIMESTAMP_FORMAT));
        
        LLMExecutionResult result = llmServiceClient.executePrompt(prompt, maxTokens, temperature);
        
        LocalDateTime endTime = LocalDateTime.now();
        Duration executionDuration = Duration.between(startTime, endTime);
        logger.info("Execution completed at: {}", endTime.format(LOG_TIMESTAMP_FORMAT));
        logger.info("Total execution time: {}.{} seconds", 
                   executionDuration.getSeconds(), 
                   executionDuration.toMillisPart());
        
        // Log results
        logResults(result);
        
            logSeparator("=", 80);
            logger.info("EXECUTION COMPLETED");
            String logFileName = String.format("llm_execution_%s.log", 
                                             LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
            logger.info("Log file would be saved as: {}", logFileName);
            logSeparator("=", 80);
            
            return result.isSuccess() ? 0 : 1;
            
        } catch (Exception e) {
            logger.error("❌ Unexpected error during execution: {}", e.getMessage(), e);
            return 1;
        }
    }
    
    private void logConfigurationSummary() {
        logger.info("Configuration Summary:");
        logger.info("  Model: {}", llmServiceClient.getModelName() != null ? llmServiceClient.getModelName() : "Not configured");
        logger.info("  Base URL: {}", llmServiceClient.getBaseUrl() != null ? llmServiceClient.getMaskedBaseUrl() : "Not configured");
        logger.info("  Service bound: {}", llmServiceClient.isServiceBound() ? "Yes" : "No");
        logger.info("  API key present: {}", llmServiceClient.hasApiKey() ? "Yes" : "No");
    }
    
    private void logCloudFoundryInfo() {
        String vcapApplication = System.getenv("VCAP_APPLICATION");
        
        if (vcapApplication != null && !vcapApplication.isEmpty()) {
            try {
                JsonNode appInfo = objectMapper.readTree(vcapApplication);
                logger.info("Cloud Foundry Application Info:");
                logger.info("  App Name: {}", getJsonValue(appInfo, "application_name", "unknown"));
                logger.info("  Version: {}", getJsonValue(appInfo, "version", "unknown"));
                logger.info("  Instance ID: {}", getJsonValue(appInfo, "instance_id", "unknown"));
                logger.info("  Space: {}", getJsonValue(appInfo, "space_name", "unknown"));
                logger.info("  Organization: {}", getJsonValue(appInfo, "organization_name", "unknown"));
            } catch (Exception e) {
                logger.warn("Could not parse VCAP_APPLICATION: {}", e.getMessage());
            }
        } else {
            logger.info("Running in local development mode (no VCAP_APPLICATION)");
        }
    }
    
    private String getJsonValue(JsonNode node, String fieldName, String defaultValue) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asText() : defaultValue;
    }
    
    private String getDefaultPrompt() {
        return Optional.ofNullable(System.getenv("LLM_PROMPT"))
                      .orElse("Hello! Please provide a brief introduction about yourself and your capabilities.");
    }
    
    private double getTemperature() {
        String tempStr = System.getenv("LLM_TEMPERATURE");
        try {
            return tempStr != null ? Double.parseDouble(tempStr) : 0.7;
        } catch (NumberFormatException e) {
            logger.warn("Invalid temperature value '{}', using default 0.7", tempStr);
            return 0.7;
        }
    }
    
    private int getMaxTokens() {
        String tokensStr = System.getenv("LLM_MAX_TOKENS");
        try {
            return tokensStr != null ? Integer.parseInt(tokensStr) : 500;
        } catch (NumberFormatException e) {
            logger.warn("Invalid max tokens value '{}', using default 500", tokensStr);
            return 500;
        }
    }
    
    private void logResults(LLMExecutionResult result) {
        logSeparator("-", 80);
        logger.info("EXECUTION RESULTS:");
        logSeparator("-", 80);
        
        if (result.isSuccess()) {
            logger.info("✅ SUCCESS: LLM prompt executed successfully");
            logger.info("Model used: {}", result.getModelUsed() != null ? result.getModelUsed() : "unknown");
            
            // Log usage statistics if available
            Map<String, Object> usage = result.getUsage();
            if (usage != null && !usage.isEmpty()) {
                logger.info("Usage Statistics:");
                logger.info("  Total tokens: {}", usage.getOrDefault("total_tokens", "N/A"));
                logger.info("  Prompt tokens: {}", usage.getOrDefault("prompt_tokens", "N/A"));
                logger.info("  Completion tokens: {}", usage.getOrDefault("completion_tokens", "N/A"));
            }
            
            logger.info("LLM Response:");
            logSeparator("-", 40);
            // Log each line of the response for better formatting
            String[] responseLines = result.getResponse().split("\\n");
            for (String line : responseLines) {
                logger.info("  {}", line);
            }
            logSeparator("-", 40);
            
        } else {
            logger.error("❌ FAILED: LLM prompt execution failed");
            logger.error("Error: {}", result.getError() != null ? result.getError() : "Unknown error");
        }
    }
    
    private void logSeparator(String character, int length) {
        logger.info(character.repeat(length));
    }
}