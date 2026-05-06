package com.cfgenai.llmapp.service;

import java.util.Map;

public class LLMExecutionResult {
    private final boolean success;
    private final String response;
    private final String error;
    private final String modelUsed;
    private final Map<String, Object> usage;
    
    private LLMExecutionResult(boolean success, String response, String error, String modelUsed, Map<String, Object> usage) {
        this.success = success;
        this.response = response;
        this.error = error;
        this.modelUsed = modelUsed;
        this.usage = usage;
    }
    
    public static LLMExecutionResult success(String response, String modelUsed, Map<String, Object> usage) {
        return new LLMExecutionResult(true, response, null, modelUsed, usage);
    }
    
    public static LLMExecutionResult error(String error) {
        return new LLMExecutionResult(false, null, error, null, null);
    }
    
    // Getters
    public boolean isSuccess() {
        return success;
    }
    
    public String getResponse() {
        return response;
    }
    
    public String getError() {
        return error;
    }
    
    public String getModelUsed() {
        return modelUsed;
    }
    
    public Map<String, Object> getUsage() {
        return usage;
    }
}