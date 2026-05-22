# LLM Warmup Application

A Java Spring Boot application that continuously executes scheduled LLM prompts via Cloud Foundry service bindings. Perfect for LLM warmup, monitoring, and scheduled AI interactions.

## 🎯 Overview

This application runs continuously on Cloud Foundry, executing LLM prompts at configurable intervals.

## ✨ Key Features

- 🚀 **Cloud Foundry Ready**: Native CF deployment with service binding support 
- 🔄 **Continuous Execution**: Scheduled prompt execution at configurable intervals (default: 5 minutes)
- ❤️ **Health Monitoring**: Built-in Spring Boot Actuator health checks prevent CF restarts
- 🛡️ **Robust Error Handling**: Graceful error handling ensures application stability
- 🔒 **Security-First**: All sensitive data (API keys, tokens) automatically masked in logs
- 🚀 **Graceful Degradation**: Starts successfully even without LLM service binding
- 📊 **Production Ready**: Comprehensive logging, metrics, and monitoring
- ☕ **Modern Stack**: Spring Boot 4.0.6, Java 21, WebClient, Reactive architecture

## 🏗️ Architecture

### Modern Spring Boot 4.0.6 Features
- **WebClient**: Reactive HTTP client for non-blocking LLM API calls
- **Java 21 LTS**: Latest performance optimizations and language features  
- **Enhanced Configuration**: Improved property binding and configuration processing
- **Actuator 4.x**: Advanced health checks and monitoring endpoints
- **Security**: Built-in credential masking and secure logging practices

### Application Flow
```
Startup → Service Discovery → Initial LLM Call → Continuous Scheduling
    ↓           ↓                ↓                     ↓
Health Check  Config Load    Log Results         Retry on Intervals
```

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Maven 3.9+  
- Cloud Foundry CLI
- Access to CF environment
- (Optional) LLM service instance bound

### 1. Build Application
```bash
cd java
mvn clean package
```
This creates `target/llm-warmup.jar`

### 2. Deploy to Cloud Foundry
```bash
# Basic deployment (no LLM service - will show errors but run)
cf push llm-warmup

# With LLM service binding (recommended)
cf bind-service llm-warmup your-ai-models-service
cf restage llm-warmup
```

### 3. Monitor Application
```bash
# Check health status
curl https://your-app-route/actuator/health

# View logs
cf logs llm-warmup --recent
```

## ⚙️ Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `LLM_PROMPT` | `"Hello! Please provide a brief introduction..."` | Prompt to execute |
| `LLM_TEMPERATURE` | `0.7` | LLM temperature parameter |
| `LLM_MAX_TOKENS` | `500` | Maximum tokens in response |
| `EXECUTION_INTERVAL_SECONDS` | `300` | Execution interval (5 minutes) |
| `OPENAI_API_KEY` | - | API key (if no service binding) |
| `LLM_BASE_URL` | `https://api.openai.com/v1` | LLM endpoint (if no service binding) |
| `LLM_MODEL` | `gpt-3.5-turbo` | Model name (if no service binding) |

### Cloud Foundry Manifest Example
```yaml
---
applications:
- name: llm-warmup
  memory: 1G
  instances: 1
  random-route: true
  buildpack: java_buildpack
  path: target/llm-warmup.jar
  
  # Health check configuration
  health-check-type: http
  health-check-http-endpoint: /actuator/health
  health-check-invocation-timeout: 60
  
  env:
    # Java 21 configuration
    JBP_CONFIG_OPEN_JDK_JRE: '{ jre: { version: 21.+ } }'
    JAVA_OPTS: "-Xmx768m -XX:MaxMetaspaceSize=128m"
    
    # Application configuration
    LLM_PROMPT: "Hello! Please introduce yourself and your capabilities."
    LLM_TEMPERATURE: "0.7"
    LLM_MAX_TOKENS: "500"
    EXECUTION_INTERVAL_SECONDS: "300"
    SPRING_PROFILES_ACTIVE: "cloud"
    
  # Optional: Bind LLM service for full functionality
  # services:
  #   - your-ai-models-service-name
```

## 🔧 Service Binding Support

### Supported Service Types
The application automatically detects and configures LLM services:

- **ai-models** (primary)
- **genai** 
- **openai**
- **azure-openai**
- **watson**

### Service Credential Mapping
```yaml
# Supported credential field names (case-insensitive):
api_key: ["api_key", "apikey", "key", "token", "access_token", "auth_token"]
base_url: ["api_base", "url", "endpoint", "base_url", "api_url", "openai_api_base"]
model: ["model", "model_name", "deployment_name", "model_id"]
```

### Nested Endpoint Support
Handles complex service credentials with nested endpoint objects:
```json
{
  "endpoint": {
    "api_base": "https://service.com/api",
    "api_key": "your-key-here"
  }
}
```

## 📊 Monitoring & Health Checks

### Health Endpoints
- **Primary**: `/actuator/health` - Main health check used by CF
- **Info**: `/actuator/info` - Application information  
- **Scheduled Tasks**: `/actuator/scheduledtasks` - View scheduled execution status

### Health Status Responses

**✅ With LLM Service Bound:**
```json
{
  "status": "UP",
  "components": {
    "llmService": {
      "status": "UP",
      "details": {
        "service": "ai-models service bound",
        "model": "gpt-3.5-turbo", 
        "baseUrl": "https://api.openai.com/***",
        "hasApiKey": true,
        "status": "LLM service fully configured"
      }
    }
  }
}
```

**⚠️ Without LLM Service:**
```json
{
  "status": "UP",
  "components": {
    "llmService": {
      "status": "UP",
      "details": {
        "service": "No ai-models service bound",
        "hasApiKey": false,
        "hasBaseUrl": false,
        "status": "LLM service configuration incomplete - will use fallback behavior",
        "message": "Application running but LLM functionality may be limited"
      }
    }
  }
}
```

## 🔒 Security Features

### Automatic Credential Masking
All sensitive information is automatically masked in logs:

```
✅ SECURE LOGGING:
Base URL found: https://api.openai.com/v1?api_key=***
API key found: [PRESENT]
Making request to: https://service.com/inference?token=***

❌ NEVER LOGGED:
- Full API keys or tokens
- Complete URLs with embedded credentials  
- Service credential objects
```

### Supported Security Patterns
- Query parameter masking (`?api_key=secret` → `?api_key=***`)
- Path-based credential masking (`/api/v1/key/secret/` → `/api/v1/key/***/`)
- Nested object credential filtering
- Case-insensitive credential detection

## 📈 Production Usage

### Deployment Patterns

**1. Continuous Warmup Service:**
```bash
# Deploy with 5-minute intervals
cf set-env llm-warmup EXECUTION_INTERVAL_SECONDS 300
cf restart llm-warmup
```

**2. High-Frequency Monitoring:**
```bash
# Deploy with 30-second intervals  
cf set-env llm-warmup EXECUTION_INTERVAL_SECONDS 30
cf restart llm-warmup
```

**3. Custom Prompts:**
```bash
# Set custom warmup prompt
cf set-env llm-warmup LLM_PROMPT "Analyze the current system status and provide recommendations."
cf restart llm-warmup
```

### Scaling Considerations
- **Memory**: 1GB recommended for stable operation
- **Instances**: Typically run as single instance (scheduler is per-instance)
- **CPU**: Low CPU usage during scheduled execution
- **Network**: Outbound HTTPS to LLM providers required

## 🛠️ Development

### Local Development
```bash
# Run locally (will attempt to find CF services, fall back to env vars)
export OPENAI_API_KEY="your-key-here"
export LLM_PROMPT="Test prompt for local development"
export EXECUTION_INTERVAL_SECONDS="60"

mvn spring-boot:run
```

### Testing Without LLM Service
The application gracefully handles missing LLM services:
```bash
# Deploy without service binding - app will start and show config errors
cf push llm-warmup

# Check health (will show UP but with warnings)
curl https://your-app-route/actuator/health
```

### Debugging
```bash
# View real-time logs
cf logs llm-warmup

# Check environment variables
cf env llm-warmup

# SSH into app for debugging
cf ssh llm-warmup

# Check app status
cf app llm-warmup
```

## 📋 Sample Log Output

### Successful Execution
```
🚀 Starting LLM Warmup Application with scheduled execution
✅ Cloud Foundry environment initialized successfully
✅ Found AI-related service by name: ai-models-service (label: ai-models)
Available service credential fields: [endpoint, model, config_url, name]
API key found: [PRESENT]
Base URL found: https://genai-proxy.sys.***
Model name found: gpt-3.5-turbo
Configured from service binding - URL: https://genai-proxy.sys.***, Model: gpt-3.5-turbo, API Key: [PRESENT]

================================================================================
LLM PROMPT EXECUTION STARTING
================================================================================
Making request to: https://genai-proxy.sys.***/chat/completions
✅ SUCCESS: LLM prompt executed successfully
Model used: gpt-3.5-turbo
Usage Statistics:
  Total tokens: 150
  Prompt tokens: 25
  Completion tokens: 125
LLM Response:
--------------------------------------------------------------------------------
  Hello! I'm Claude, an AI assistant created by Anthropic...
--------------------------------------------------------------------------------
================================================================================
EXECUTION COMPLETED
================================================================================
⏰ Scheduled execution completed. Next execution in 300 seconds.
```

### Without Service Binding
```
🚀 Starting LLM Warmup Application with scheduled execution
⚠️ Cloud Foundry environment not available - running in local mode
❌ No ai-models service found. Looking for service type: ai-models
Make sure your ai-models service is bound and has this name, label, or tag

❌ FAILED: LLM prompt execution failed  
Error: No API key configured. Please check your service binding or environment variables.
Application will continue running and retry on scheduled executions.
```

## 🚨 Troubleshooting

### Common Issues

**1. App Not Starting:**
- Check Java 21 is configured: `JBP_CONFIG_OPEN_JDK_JRE: '{ jre: { version: 21.+ } }'`
- Verify memory allocation (1GB minimum recommended)
- Check logs: `cf logs llm-warmup --recent`

**2. LLM Calls Failing:**
- Verify service binding: `cf services` and `cf env llm-warmup`
- Check API key in service credentials
- Verify network connectivity to LLM provider

**3. Health Check Failing:**
- Ensure `/actuator/health` endpoint is accessible
- Check `health-check-invocation-timeout` (recommend 60s)
- Verify app is responding on correct port

**4. Scheduled Execution Not Working:**
- Check `EXECUTION_INTERVAL_SECONDS` environment variable
- Verify app logs show scheduled execution messages
- Ensure app is not restarting due to health check failures

### Support Commands
```bash
# Complete diagnostic information
cf app llm-warmup
cf env llm-warmup  
cf logs llm-warmup --recent
curl https://your-app-route/actuator/health
curl https://your-app-route/actuator/scheduledtasks
```

## 🎯 Use Cases

- **LLM Warmup**: Keep LLM services active and responsive
- **Service Monitoring**: Continuous health checks for AI services  
- **Scheduled Interactions**: Regular AI model testing and validation
- **Performance Baselines**: Consistent load for performance monitoring
- **Integration Testing**: Automated testing of LLM integrations

## 📄 License

See the main repository for licensing information.
