package com.sauron.common.config;

import com.google.ai.client.generativeai.GenerativeModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gemini AI 설정 클래스
 * Google Generative AI 클라이언트 설정을 담당합니다.
 */
@Configuration
@Slf4j
public class GeminiConfig {
    
    @Value("${gemini.api.key}")
    private String apiKey;
    
    @Value("${gemini.api.model:gemini-1.5-flash}")
    private String modelName;
    
    @Value("${gemini.api.timeout:30s}")
    private String timeout;
    
    @Value("${gemini.api.max-retries:3}")
    private int maxRetries;
    
    /**
     * Gemini Generative Model 빈 생성
     */
    @Bean
    public GenerativeModel generativeModel() {
        if (apiKey == null || apiKey.trim().isEmpty() || "your-api-key-here".equals(apiKey)) {
            log.warn("Gemini API key not configured. Using stub implementation.");
            return null; // 스텁 모드로 동작
        }
        
        GenerativeModel model = new GenerativeModel(modelName, apiKey);
        
        log.info("Gemini GenerativeModel configured - Model: {}, Timeout: {}, MaxRetries: {}", 
                modelName, timeout, maxRetries);
        
        return model;
    }
}