package com.sauron.common.config;

import com.google.genai.Client;
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
    
    @Value("${gemini.api.model:gemini-2.5-flash}")
    private String modelName;
    
    @Value("${gemini.api.timeout:30s}")
    private String timeout;
    
    @Value("${gemini.api.max-retries:3}")
    private int maxRetries;
    
    /**
     * Gemini Client 빈 생성
     */
    @Bean
    public Client geminiClient() {
        if (apiKey == null || apiKey.trim().isEmpty() || "your-api-key-here".equals(apiKey)) {
            log.warn("Gemini API key not configured. Using stub implementation.");
            return null; // 스텁 모드로 동작
        }
        
        try {
            // GEMINI_API_KEY 환경 변수 설정
            System.setProperty("GEMINI_API_KEY", apiKey);
            
            Client client = new Client();
            
            log.info("Gemini Client configured - Model: {}, Timeout: {}, MaxRetries: {}", 
                    modelName, timeout, maxRetries);
            
            return client;
        } catch (Exception e) {
            log.error("Failed to initialize Gemini client", e);
            return null;
        }
    }
    
    /**
     * 모델명 반환
     */
    @Bean
    public String geminiModelName() {
        return modelName;
    }
}