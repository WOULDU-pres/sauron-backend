package com.sauron.common.external;

import com.sauron.common.external.GeminiWorkerClient.AnalysisResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GeminiWorkerClient.AnalysisResult 테스트
 */
class GeminiWorkerClientAnalysisResultTest {

    @Test
    void testAnalysisResultBuilder() {
        // Given
        String detectedType = "spam";
        Double confidenceScore = 0.85;
        String metadata = "스팸 키워드 감지";
        String messageId = "msg-123";
        long processingTimeMs = 150L;

        // When
        AnalysisResult result = AnalysisResult.builder()
                .detectedType(detectedType)
                .confidenceScore(confidenceScore)
                .metadata(metadata)
                .messageId(messageId)
                .processingTimeMs(processingTimeMs)
                .build();

        // Then
        assertEquals(detectedType, result.getDetectedType());
        assertEquals(confidenceScore, result.getConfidenceScore());
        assertEquals(metadata, result.getMetadata());
        assertEquals(messageId, result.getMessageId());
        assertEquals(processingTimeMs, result.getProcessingTimeMs());
    }

    @Test
    void testAnalysisResultConstructor() {
        // Given
        String detectedType = "normal";
        Double confidenceScore = 0.95;
        String metadata = "정상 메시지";

        // When
        AnalysisResult result = new AnalysisResult(detectedType, confidenceScore, metadata);

        // Then
        assertEquals(detectedType, result.getDetectedType());
        assertEquals(confidenceScore, result.getConfidenceScore());
        assertEquals(metadata, result.getMetadata());
        assertNull(result.getMessageId());
        assertEquals(0L, result.getProcessingTimeMs());
    }

    @Test
    void testGetReasoningCompatibility() {
        // Given
        String metadata = "분석 결과 설명";
        AnalysisResult result = new AnalysisResult("normal", 0.9, metadata);

        // When & Then - getReasoning()은 metadata와 같아야 함
        assertEquals(metadata, result.getReasoning());
        assertEquals(metadata, result.getMetadata());
    }
}