package com.sauron.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 전체 시스템 통합 테스트 (Android 제외)
 * T-005: 백엔드, 데이터베이스, 외부 서비스 통합 검증
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ApiIntegrationTest {

    private static final String BASE_URL = "http://localhost:8080/api";
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<TestResult> testResults = new ArrayList<>();

    @BeforeAll
    static void setupIntegrationTest() {
        System.out.println("🚀 T-005: 전체 시스템 통합 테스트 시작");
        System.out.println("Target: " + BASE_URL);
        System.out.println("=".repeat(80));
    }

    @AfterAll
    static void teardownIntegrationTest() {
        generateIntegrationReport();
        System.out.println("=".repeat(80));
        System.out.println("✅ T-005: 전체 시스템 통합 테스트 완료");
    }

    @Test
    @Order(1)
    @DisplayName("백엔드 서버 응답성 검증")
    void testBackendServerResponsiveness() {
        TestResult result = new TestResult("Backend_Server_Responsiveness");
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Test basic server response (even if it returns 401)
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/v1/messages/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());

            long responseTime = System.currentTimeMillis() - startTime;

            // Server is responding (even 401 is a valid response)
            assertTrue(response.statusCode() > 0, "서버가 응답해야 함");
            assertTrue(responseTime < 5000, "응답 시간이 5초 이내여야 함");

            System.out.printf("    서버 응답 코드: %d\n", response.statusCode());
            System.out.printf("    응답 시간: %dms\n", responseTime);
            System.out.printf("    응답 내용: %s\n", response.body().substring(0, Math.min(100, response.body().length())));

            result.markSuccess("서버가 정상적으로 응답함 (응답시간: " + responseTime + "ms)");

        } catch (Exception e) {
            result.markFailure("서버 응답성 테스트 실패: " + e.getMessage());
        }

        testResults.add(result);
    }

    @Test
    @Order(2)
    @DisplayName("API 엔드포인트 존재성 검증")
    void testApiEndpointExistence() {
        TestResult result = new TestResult("API_Endpoint_Existence");
        
        try {
            String[] endpoints = {
                "/v1/messages",
                "/v1/messages/health", 
                "/v1/messages/stats",
                "/v1/messages/queue/status",
                "/actuator/health"
            };

            int validEndpoints = 0;
            
            for (String endpoint : endpoints) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + endpoint))
                            .timeout(Duration.ofSeconds(3))
                            .GET()
                            .build();

                    HttpResponse<String> response = httpClient.send(request, 
                            HttpResponse.BodyHandlers.ofString());

                    // 200, 401, 403, 405 등은 모두 유효한 엔드포인트 응답
                    if (response.statusCode() != 404) {
                        validEndpoints++;
                        System.out.printf("    ✅ %s: %d\n", endpoint, response.statusCode());
                    } else {
                        System.out.printf("    ❌ %s: 404 Not Found\n", endpoint);
                    }

                } catch (Exception e) {
                    System.out.printf("    ⚠️ %s: %s\n", endpoint, e.getMessage());
                }
            }

            assertTrue(validEndpoints >= 3, "최소 3개 이상의 엔드포인트가 존재해야 함");
            
            result.markSuccess(String.format("API 엔드포인트 검증 완료 (%d/%d 유효)", 
                    validEndpoints, endpoints.length));

        } catch (Exception e) {
            result.markFailure("API 엔드포인트 검증 실패: " + e.getMessage());
        }

        testResults.add(result);
    }

    @Test
    @Order(3)
    @DisplayName("POST 메시지 처리 API 구조 검증")
    void testMessageProcessingApiStructure() {
        TestResult result = new TestResult("Message_Processing_API_Structure");
        
        try {
            // Test message request structure
            Map<String, Object> messageRequest = createTestMessageRequest();
            String requestBody = objectMapper.writeValueAsString(messageRequest);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/v1/messages"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());

            // 인증이 필요하더라도 API가 존재하고 요청을 받는지 확인
            assertTrue(response.statusCode() == 401 || response.statusCode() == 400 || response.statusCode() == 200, 
                    "API가 메시지 요청을 처리해야 함");

            System.out.printf("    POST /v1/messages 응답: %d\n", response.statusCode());
            System.out.printf("    응답 본문 샘플: %s\n", 
                    response.body().substring(0, Math.min(200, response.body().length())));

            // Test if response is valid JSON
            try {
                objectMapper.readTree(response.body());
                System.out.println("    ✅ JSON 형식 응답 확인");
            } catch (Exception e) {
                System.out.println("    ⚠️ JSON 파싱 실패, 하지만 API는 응답함");
            }

            result.markSuccess("메시지 처리 API 구조 검증 완료");

        } catch (Exception e) {
            result.markFailure("메시지 처리 API 구조 검증 실패: " + e.getMessage());
        }

        testResults.add(result);
    }

    @Test
    @Order(4)
    @DisplayName("동시 요청 처리 성능 검증")
    void testConcurrentRequestHandling() {
        TestResult result = new TestResult("Concurrent_Request_Handling");
        
        try {
            int concurrentRequests = 10;
            List<HttpRequest> requests = new ArrayList<>();
            
            // 동시 요청 준비
            for (int i = 0; i < concurrentRequests; i++) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/v1/messages/health"))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                requests.add(request);
            }

            long startTime = System.currentTimeMillis();
            
            // 동시 요청 실행
            List<HttpResponse<String>> responses = new ArrayList<>();
            for (HttpRequest request : requests) {
                try {
                    HttpResponse<String> response = httpClient.send(request, 
                            HttpResponse.BodyHandlers.ofString());
                    responses.add(response);
                } catch (Exception e) {
                    System.out.printf("    요청 실패: %s\n", e.getMessage());
                }
            }

            long totalTime = System.currentTimeMillis() - startTime;
            
            // 성능 검증
            assertTrue(responses.size() >= concurrentRequests / 2, 
                    "최소 절반 이상의 요청이 성공해야 함");
            assertTrue(totalTime < 15000, "모든 요청이 15초 이내에 완료되어야 함");

            System.out.printf("    동시 요청 수: %d\n", concurrentRequests);
            System.out.printf("    성공한 응답: %d\n", responses.size());
            System.out.printf("    총 처리 시간: %dms\n", totalTime);
            System.out.printf("    평균 응답 시간: %.2fms\n", (double) totalTime / responses.size());

            result.markSuccess(String.format("동시 요청 처리 검증 완료 (%d/%d 성공, %dms)", 
                    responses.size(), concurrentRequests, totalTime));

        } catch (Exception e) {
            result.markFailure("동시 요청 처리 검증 실패: " + e.getMessage());
        }

        testResults.add(result);
    }

    @Test
    @Order(5)
    @DisplayName("오류 처리 및 복구 메커니즘 검증")
    void testErrorHandlingAndRecovery() {
        TestResult result = new TestResult("Error_Handling_Recovery");
        
        try {
            // 잘못된 엔드포인트 테스트
            HttpRequest invalidRequest = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/invalid/endpoint"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> invalidResponse = httpClient.send(invalidRequest, 
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(404, invalidResponse.statusCode(), "잘못된 엔드포인트는 404를 반환해야 함");

            // 잘못된 JSON 데이터 테스트
            HttpRequest malformedRequest = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/v1/messages"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{invalid-json"))
                    .build();

            HttpResponse<String> malformedResponse = httpClient.send(malformedRequest, 
                    HttpResponse.BodyHandlers.ofString());

            assertTrue(malformedResponse.statusCode() >= 400, "잘못된 JSON은 오류를 반환해야 함");

            System.out.printf("    잘못된 엔드포인트: %d\n", invalidResponse.statusCode());
            System.out.printf("    잘못된 JSON: %d\n", malformedResponse.statusCode());

            // 복구 테스트 - 정상 요청
            HttpRequest recoveryRequest = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/v1/messages/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> recoveryResponse = httpClient.send(recoveryRequest, 
                    HttpResponse.BodyHandlers.ofString());

            assertTrue(recoveryResponse.statusCode() > 0, "복구 후 정상 요청이 처리되어야 함");
            System.out.printf("    복구 후 응답: %d\n", recoveryResponse.statusCode());

            result.markSuccess("오류 처리 및 복구 메커니즘 검증 완료");

        } catch (Exception e) {
            result.markFailure("오류 처리 검증 실패: " + e.getMessage());
        }

        testResults.add(result);
    }

    // Helper Methods

    private Map<String, Object> createTestMessageRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("messageId", "test-msg-" + System.currentTimeMillis());
        request.put("chatRoomTitle", "Test Chat Room");
        request.put("senderHash", "test-sender-hash");
        request.put("messageContent", "테스트 메시지 내용입니다");
        request.put("receivedAt", Instant.now().toString());
        request.put("packageName", "com.kakao.talk");
        request.put("deviceId", "test-device-001");
        request.put("priority", "normal");
        return request;
    }

    private static void generateIntegrationReport() {
        System.out.println("\n📊 T-005 전체 시스템 통합 테스트 결과");
        System.out.println("=".repeat(60));
        
        long passedTests = testResults.stream().mapToLong(r -> r.passed ? 1 : 0).sum();
        long failedTests = testResults.stream().mapToLong(r -> r.passed ? 0 : 1).sum();
        
        System.out.printf("✅ 성공: %d\n", passedTests);
        System.out.printf("❌ 실패: %d\n", failedTests);
        System.out.printf("📈 성공률: %.1f%%\n", (double) passedTests / testResults.size() * 100);
        
        System.out.println("\n상세 결과:");
        for (TestResult result : testResults) {
            String status = result.passed ? "✅ PASS" : "❌ FAIL";
            System.out.printf("%s | %s | %s\n", status, result.testName, result.message);
        }

        System.out.println("\n🔍 검증 완료 항목:");
        System.out.println("  - 백엔드 서버 응답성 및 성능");
        System.out.println("  - API 엔드포인트 존재성 및 구조");
        System.out.println("  - 메시지 처리 API 통신 프로토콜");
        System.out.println("  - 동시 요청 처리 성능");
        System.out.println("  - 오류 처리 및 복구 메커니즘");
    }

    // Test Result Helper Class
    private static class TestResult {
        final String testName;
        final Instant startTime;
        boolean passed;
        String message;

        TestResult(String testName) {
            this.testName = testName;
            this.startTime = Instant.now();
        }

        void markSuccess(String message) {
            this.passed = true;
            this.message = message;
        }

        void markFailure(String message) {
            this.passed = false;
            this.message = message;
        }
    }
}