package com.cfgenai.llmapp.service;

import io.pivotal.cfenv.core.CfEnv;
import io.pivotal.cfenv.core.CfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class CloudFoundryServiceConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(CloudFoundryServiceConfig.class);
    private static final String LLM_SERVICE_TYPE = "ai-models";
    
    private final CfEnv cfEnv;
    private CfService llmService;
    private Map<String, Object> serviceCredentials;
    
    public CloudFoundryServiceConfig() {
        CfEnv tempCfEnv = null;
        try {
            tempCfEnv = new CfEnv();
            logger.info("✅ Cloud Foundry environment initialized successfully");
        } catch (Exception e) {
            logger.warn("⚠️ Could not initialize Cloud Foundry environment: {}. Running in local mode.", e.getMessage());
        }
        this.cfEnv = tempCfEnv;
        extractServiceConfiguration();
    }
    
    private void extractServiceConfiguration() {
        if (cfEnv == null) {
            logger.warn("Cloud Foundry environment not available - running in local mode");
            return;
        }
        
        if (!cfEnv.isInCf()) {
            logger.warn("Not running in Cloud Foundry - running in local mode");
            return;
        }
        
        try {
            // Get all services first for debugging
            List<CfService> allServices = cfEnv.findAllServices();
            logger.info("Found {} total services in Cloud Foundry", allServices.size());
            
            // Log all available services for debugging
            for (CfService service : allServices) {
                logger.info("Available service: {} (label: {}, tags: {})", 
                    service.getName(), service.getLabel(), service.getTags());
            }
            
            // Try to find ai-models service by name first (most common pattern)
            try {
                CfService service = cfEnv.findServiceByName(LLM_SERVICE_TYPE);
                if (service != null) {
                    llmService = service;
                    serviceCredentials = service.getCredentials().getMap();
                    logger.info("✅ Found LLM service by name: {} (label: {})", service.getName(), service.getLabel());
                    logCredentialFields();
                    return;
                }
            } catch (Exception e) {
                logger.debug("Service with name '{}' not found: {}", LLM_SERVICE_TYPE, e.getMessage());
            }
            
            // Try to find by label
            try {
                List<CfService> services = cfEnv.findServicesByLabel(LLM_SERVICE_TYPE);
                if (!services.isEmpty()) {
                    llmService = services.get(0);
                    serviceCredentials = llmService.getCredentials().getMap();
                    logger.info("✅ Found LLM service by label: {} (name: {})", LLM_SERVICE_TYPE, llmService.getName());
                    logCredentialFields();
                    return;
                }
            } catch (Exception e) {
                logger.debug("Service with label '{}' not found: {}", LLM_SERVICE_TYPE, e.getMessage());
            }
            
            // Try to find by tags
            try {
                List<CfService> services = cfEnv.findServicesByTag(LLM_SERVICE_TYPE);
                if (!services.isEmpty()) {
                    llmService = services.get(0);
                    serviceCredentials = llmService.getCredentials().getMap();
                    logger.info("✅ Found LLM service by tag: {} (name: {}, label: {})", 
                        LLM_SERVICE_TYPE, llmService.getName(), llmService.getLabel());
                    logCredentialFields();
                    return;
                }
            } catch (Exception e) {
                logger.debug("Service with tag '{}' not found: {}", LLM_SERVICE_TYPE, e.getMessage());
            }
            
            // Fallback: look for any service that might be genai-related
            for (CfService service : allServices) {
                String serviceName = service.getName().toLowerCase();
                String serviceLabel = service.getLabel().toLowerCase();
                
                if (serviceName.contains("ai-models") || serviceName.contains("genai") || serviceName.contains("ai") ||
                    serviceLabel.contains("ai-models") || serviceLabel.contains("genai") || serviceLabel.contains("ai")) {
                    
                    llmService = service;
                    serviceCredentials = service.getCredentials().getMap();
                    logger.info("✅ Found AI-related service by pattern matching: {} (label: {})", 
                        service.getName(), service.getLabel());
                    logCredentialFields();
                    return;
                }
            }
            
            // Last resort: use first service with credentials
            for (CfService service : allServices) {
                Map<String, Object> creds = service.getCredentials().getMap();
                if (creds != null && !creds.isEmpty()) {
                    llmService = service;
                    serviceCredentials = creds;
                    logger.warn("⚠️ Using first available service as fallback: {} (label: {})", 
                        service.getName(), service.getLabel());
                    logCredentialFields();
                    return;
                }
            }
            
            logger.error("❌ No ai-models service found. Looking for service type: {}", LLM_SERVICE_TYPE);
            logger.info("Make sure your ai-models service is bound and has this name, label, or tag");
            
        } catch (Exception e) {
            logger.error("Failed to extract service configuration: {}", e.getMessage(), e);
        }
    }
    
    private void logCredentialFields() {
        if (serviceCredentials != null && !serviceCredentials.isEmpty()) {
            logger.info("Available credential fields: [{}]", String.join(", ", serviceCredentials.keySet()));
            
            // Log the structure of each credential field for debugging both old and new formats
            for (Map.Entry<String, Object> entry : serviceCredentials.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String) {
                    logger.info("Credential '{}': {} (String)", entry.getKey(), maskCredentialValue(entry.getKey(), (String) value));
                } else if (value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> nestedMap = (Map<String, Object>) value;
                    logger.info("Credential '{}': nested object with fields [{}]", entry.getKey(), String.join(", ", nestedMap.keySet()));
                    
                    // Show nested field values too
                    for (Map.Entry<String, Object> nestedEntry : nestedMap.entrySet()) {
                        logger.info("  {}.{}: {} ({})", entry.getKey(), nestedEntry.getKey(), 
                            maskCredentialValue(nestedEntry.getKey(), String.valueOf(nestedEntry.getValue())), 
                            nestedEntry.getValue().getClass().getSimpleName());
                    }
                } else if (value instanceof java.util.List) {
                    logger.info("Credential '{}': array with {} elements", entry.getKey(), ((java.util.List<?>) value).size());
                } else {
                    logger.info("Credential '{}': {} ({})", entry.getKey(), value, value.getClass().getSimpleName());
                }
            }
        }
    }
    
    public Map<String, Object> getServiceCredentials() {
        return serviceCredentials;
    }
    
    public CfService getLlmService() {
        return llmService;
    }
    
    public boolean hasServiceBinding() {
        return llmService != null && serviceCredentials != null;
    }
    
    public String getCredentialValue(String... fieldNames) {
        if (serviceCredentials == null) {
            return null;
        }
        
        // Log all available credentials for debugging
        logger.info("Searching for credential fields: {} in available fields: {}", 
            Arrays.toString(fieldNames), serviceCredentials.keySet());
        
        // First pass: look for direct string fields at top level
        for (String fieldName : fieldNames) {
            Object value = serviceCredentials.get(fieldName);
            if (value instanceof String && !((String) value).isEmpty()) {
                logger.info("Found direct credential value for field '{}': {}", fieldName, maskCredentialValue(fieldName, (String) value));
                return (String) value;
            }
        }
        
        // Second pass: search in ALL nested objects for any of the field names we're looking for
        for (String fieldName : fieldNames) {
            for (Map.Entry<String, Object> credEntry : serviceCredentials.entrySet()) {
                Object credValue = credEntry.getValue();
                if (credValue instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> nestedMap = (Map<String, Object>) credValue;
                    logger.info("Searching nested object '{}' for field '{}'", credEntry.getKey(), fieldName);
                    
                    // Direct match in nested object
                    Object nestedValue = nestedMap.get(fieldName);
                    if (nestedValue instanceof String && !((String) nestedValue).isEmpty()) {
                        logger.info("Found nested credential value for field '{}' in '{}' object: {}", 
                            fieldName, credEntry.getKey(), maskCredentialValue(fieldName, (String) nestedValue));
                        return (String) nestedValue;
                    }
                    
                    // Try case-insensitive match in nested object
                    for (Map.Entry<String, Object> nestedEntry : nestedMap.entrySet()) {
                        if (nestedEntry.getKey().toLowerCase().equals(fieldName.toLowerCase()) &&
                            nestedEntry.getValue() instanceof String && 
                            !((String) nestedEntry.getValue()).isEmpty()) {
                            logger.info("Found nested credential value for field '{}' (case variation '{}') in '{}' object: {}", 
                                fieldName, nestedEntry.getKey(), credEntry.getKey(), 
                                maskCredentialValue(fieldName, (String) nestedEntry.getValue()));
                            return (String) nestedEntry.getValue();
                        }
                    }
                }
            }
        }
        
        // Third pass: try case-insensitive search at top level
        for (String fieldName : fieldNames) {
            for (Map.Entry<String, Object> entry : serviceCredentials.entrySet()) {
                if (entry.getKey().toLowerCase().equals(fieldName.toLowerCase())) {
                    Object value = entry.getValue();
                    if (value instanceof String && !((String) value).isEmpty()) {
                        logger.info("Found credential value for field '{}' (case-insensitive): {}", fieldName, maskCredentialValue(fieldName, (String) value));
                        return (String) value;
                    }
                }
            }
        }
        
        logger.info("No value found for any of the credential fields: {}", Arrays.toString(fieldNames));
        return null;
    }
    
    /**
     * Mask sensitive credential values for safe logging
     * @param fieldName the field name to determine masking behavior
     * @param value the credential value to mask
     * @return masked value safe for logging
     */
    private String maskCredentialValue(String fieldName, String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        
        String fieldLower = fieldName.toLowerCase();
        
        // Completely mask API keys, tokens, secrets, passwords
        if (fieldLower.contains("key") || fieldLower.contains("token") || 
            fieldLower.contains("secret") || fieldLower.contains("password") ||
            fieldLower.contains("auth") || fieldLower.contains("credential")) {
            return "***";
        }
        
        // For URLs, use the URL masking logic
        if (fieldLower.contains("url") || fieldLower.contains("endpoint") || 
            fieldLower.contains("base") || fieldLower.contains("host")) {
            return maskSensitiveUrl(value);
        }
        
        // For other fields (model names, etc.), show the actual value
        return value;
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