package com.sauron.detector.service;

import com.sauron.detector.entity.AnnouncementAlert;
import com.sauron.detector.entity.AnnouncementDetection;
import com.sauron.detector.repository.AnnouncementAlertRepository;
import com.sauron.detector.repository.AnnouncementDetectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 공지사항 시스템 고급 분석 및 리포팅 서비스
 * 
 * T-007-004 요구사항:
 * - 고급 분석 및 리포팅 시스템
 * - 시간대별 알림 발송 패턴 분석
 * - 채널별 성능 비교 리포트
 * - 관리자 대응 시간 분석
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnnouncementSystemAnalyticsService {

    private final AnnouncementDetectionRepository detectionRepository;
    private final AnnouncementAlertRepository alertRepository;

    /**
     * 종합 성능 분석 리포트 생성
     */
    public ComprehensiveAnalyticsReport generateComprehensiveReport(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("종합 성능 분석 리포트 생성 시작: {} ~ {}", 
                startDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                endDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        ComprehensiveAnalyticsReport report = new ComprehensiveAnalyticsReport();
        
        // 기본 통계 수집
        List<AnnouncementDetection> detections = detectionRepository.findByDetectedAtBetween(startDate, endDate);
        List<AnnouncementAlert> alerts = alertRepository.findByCreatedAtBetween(startDate, endDate);
        
        report.setPeriod(startDate + " ~ " + endDate);
        report.setDetectionAnalysis(analyzeDetectionPerformance(detections));
        report.setAlertAnalysis(analyzeAlertPerformance(alerts));
        report.setTimePatternAnalysis(analyzeTimePatterns(detections));
        report.setChannelPerformanceAnalysis(analyzeChannelPerformance(alerts));
        report.setQualityMetrics(calculateQualityMetrics(detections, alerts));
        report.setRecommendations(generateRecommendations(detections, alerts));

        log.info("종합 성능 분석 리포트 생성 완료");
        return report;
    }

    /**
     * 감지 성능 분석
     */
    private DetectionAnalysis analyzeDetectionPerformance(List<AnnouncementDetection> detections) {
        DetectionAnalysis analysis = new DetectionAnalysis();
        
        if (detections.isEmpty()) {
            analysis.setTotalMessages(0);
            return analysis;
        }

        analysis.setTotalMessages(detections.size());
        
        // 신뢰도 분포 분석
        Map<String, Long> confidenceDistribution = detections.stream()
                .collect(Collectors.groupingBy(
                        d -> getConfidenceRange(d.getConfidenceScore()),
                        Collectors.counting()
                ));
        analysis.setConfidenceDistribution(confidenceDistribution);

        // 높은 신뢰도 감지 (85% 이상)
        long highConfidenceDetections = detections.stream()
                .mapToLong(d -> d.getConfidenceScore().compareTo(BigDecimal.valueOf(0.85)) >= 0 ? 1 : 0)
                .sum();
        analysis.setHighConfidenceDetections(highConfidenceDetections);
        analysis.setHighConfidenceRate((double) highConfidenceDetections / detections.size());

        // 패턴별 감지 통계
        Map<String, Long> patternStats = detections.stream()
                .filter(d -> d.getPatternMatched() != null)
                .collect(Collectors.groupingBy(
                        AnnouncementDetection::getPatternMatched,
                        Collectors.counting()
                ));
        analysis.setPatternMatchingStats(patternStats);

        // 키워드 분석
        Map<String, Long> keywordStats = detections.stream()
                .filter(d -> d.getKeywordsMatched() != null)
                .flatMap(d -> Arrays.stream(d.getKeywordsMatched().split(",")))
                .map(String::trim)
                .filter(k -> !k.isEmpty())
                .collect(Collectors.groupingBy(k -> k, Collectors.counting()));
        analysis.setTopKeywords(keywordStats.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                )));

        return analysis;
    }

    /**
     * 알림 성능 분석
     */
    private AlertAnalysis analyzeAlertPerformance(List<AnnouncementAlert> alerts) {
        AlertAnalysis analysis = new AlertAnalysis();
        
        if (alerts.isEmpty()) {
            analysis.setTotalAlerts(0);
            return analysis;
        }

        analysis.setTotalAlerts(alerts.size());

        // 전송 상태별 통계
        Map<String, Long> statusDistribution = alerts.stream()
                .collect(Collectors.groupingBy(
                        alert -> alert.getDeliveryStatus().name(),
                        Collectors.counting()
                ));
        analysis.setStatusDistribution(statusDistribution);

        // 성공률 계산
        long successfulAlerts = alerts.stream()
                .mapToLong(a -> a.getDeliveryStatus() == AnnouncementAlert.DeliveryStatus.DELIVERED || a.getDeliveryStatus() == AnnouncementAlert.DeliveryStatus.SENT ? 1 : 0)
                .sum();
        analysis.setSuccessRate((double) successfulAlerts / alerts.size());

        // 우선순위별 분석
        Map<String, Long> priorityDistribution = alerts.stream()
                .filter(a -> a.getAlertType() != null)
                .collect(Collectors.groupingBy(
                        AnnouncementAlert::getAlertType,
                        Collectors.counting()
                ));
        analysis.setPriorityDistribution(priorityDistribution);

        // 재시도 통계
        double avgRetryCount = alerts.stream()
                .mapToInt(AnnouncementAlert::getRetryCount)
                .average()
                .orElse(0.0);
        analysis.setAverageRetryCount(avgRetryCount);

        return analysis;
    }

    /**
     * 시간 패턴 분석
     */
    private TimePatternAnalysis analyzeTimePatterns(List<AnnouncementDetection> detections) {
        TimePatternAnalysis analysis = new TimePatternAnalysis();

        if (detections.isEmpty()) {
            return analysis;
        }

        // 시간대별 분포
        Map<Integer, Long> hourlyDistribution = detections.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getDetectedAt().getHour(),
                        Collectors.counting()
                ));
        analysis.setHourlyDistribution(hourlyDistribution);

        // 요일별 분포
        Map<String, Long> dayOfWeekDistribution = detections.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getDetectedAt().getDayOfWeek().toString(),
                        Collectors.counting()
                ));
        analysis.setDayOfWeekDistribution(dayOfWeekDistribution);

        // 피크 시간대 식별
        analysis.setPeakHour(hourlyDistribution.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> String.format("%02d시 (%d건)", entry.getKey(), entry.getValue()))
                .orElse("데이터 없음"));

        // 업무시간 vs 업무시간 외
        long businessHoursCount = detections.stream()
                .mapToLong(d -> {
                    int hour = d.getDetectedAt().getHour();
                    return (hour >= 9 && hour <= 18) ? 1 : 0;
                })
                .sum();
        analysis.setBusinessHoursRatio((double) businessHoursCount / detections.size());

        return analysis;
    }

    /**
     * 채널별 성능 분석
     */
    private ChannelPerformanceAnalysis analyzeChannelPerformance(List<AnnouncementAlert> alerts) {
        ChannelPerformanceAnalysis analysis = new ChannelPerformanceAnalysis();

        if (alerts.isEmpty()) {
            return analysis;
        }

        // 채널별 성공률 (channel 필드와 delivery status 사용)
        Map<String, ChannelStats> channelStats = new HashMap<>();
        
        for (AnnouncementAlert alert : alerts) {
            String channel = alert.getChannel();
            boolean isSuccess = alert.getDeliveryStatus() == AnnouncementAlert.DeliveryStatus.DELIVERED;
            
            updateChannelStats(channelStats, channel, isSuccess);
        }

        analysis.setChannelStats(channelStats);

        // 다중 채널 성공 패턴 분석 (성공한 알림 기준)
        long multiChannelSuccess = alerts.stream()
                .mapToLong(a -> a.getDeliveryStatus() == AnnouncementAlert.DeliveryStatus.DELIVERED ? 1 : 0)
                .sum();
        analysis.setMultiChannelSuccessRate((double) multiChannelSuccess / alerts.size());

        return analysis;
    }

    /**
     * 품질 지표 계산
     */
    private QualityMetrics calculateQualityMetrics(List<AnnouncementDetection> detections, List<AnnouncementAlert> alerts) {
        QualityMetrics metrics = new QualityMetrics();

        if (!detections.isEmpty()) {
            // 정확도 지표
            double avgConfidence = detections.stream()
                    .mapToDouble(d -> d.getConfidenceScore().doubleValue())
                    .average()
                    .orElse(0.0);
            metrics.setAverageConfidence(avgConfidence);

            // 95% 목표 달성률
            long meetingTarget = detections.stream()
                    .mapToLong(d -> d.getConfidenceScore().compareTo(BigDecimal.valueOf(0.95)) >= 0 ? 1 : 0)
                    .sum();
            metrics.setTargetAchievementRate((double) meetingTarget / detections.size());

            // 일관성 지표 (표준편차)
            double variance = detections.stream()
                    .mapToDouble(d -> Math.pow(d.getConfidenceScore().doubleValue() - avgConfidence, 2))
                    .average()
                    .orElse(0.0);
            metrics.setConsistencyScore(1.0 - Math.sqrt(variance)); // 낮은 분산 = 높은 일관성
        }

        if (!alerts.isEmpty()) {
            // 알림 품질 지표
            long successfulAlerts = alerts.stream()
                    .mapToLong(a -> "SENT".equals(a.getDeliveryStatus()) ? 1 : 0)
                    .sum();
            metrics.setAlertReliability((double) successfulAlerts / alerts.size());
        }

        // 전체 시스템 품질 점수
        metrics.setOverallQualityScore(calculateOverallQualityScore(metrics));

        return metrics;
    }

    /**
     * 개선 권장사항 생성
     */
    private List<String> generateRecommendations(List<AnnouncementDetection> detections, List<AnnouncementAlert> alerts) {
        List<String> recommendations = new ArrayList<>();

        if (detections.isEmpty() && alerts.isEmpty()) {
            recommendations.add("데이터가 충분하지 않습니다. 더 많은 운영 데이터 수집이 필요합니다.");
            return recommendations;
        }

        // 감지 성능 관련 권장사항
        if (!detections.isEmpty()) {
            double avgConfidence = detections.stream()
                    .mapToDouble(d -> d.getConfidenceScore().doubleValue())
                    .average()
                    .orElse(0.0);

            if (avgConfidence < 0.90) {
                recommendations.add("🎯 감지 정확도 개선 필요: 현재 평균 신뢰도 " + String.format("%.1f%%", avgConfidence * 100) + 
                                   ". AI 모델 재훈련 또는 패턴 규칙 업데이트를 권장합니다.");
            }

            long lowConfidenceCount = detections.stream()
                    .mapToLong(d -> d.getConfidenceScore().compareTo(BigDecimal.valueOf(0.7)) < 0 ? 1 : 0)
                    .sum();
            if (lowConfidenceCount > detections.size() * 0.1) {
                recommendations.add("⚠️ 저신뢰도 감지 비율 높음: 전체의 " + 
                                   String.format("%.1f%%", (double) lowConfidenceCount / detections.size() * 100) + 
                                   ". 화이트리스트 또는 예외 규칙 추가를 검토하세요.");
            }
        }

        // 알림 성능 관련 권장사항
        if (!alerts.isEmpty()) {
            long failedAlerts = alerts.stream()
                    .mapToLong(a -> a.getDeliveryStatus() == AnnouncementAlert.DeliveryStatus.FAILED ? 1 : 0)
                    .sum();
            if (failedAlerts > alerts.size() * 0.05) {
                recommendations.add("📱 알림 전송 실패율 높음: " + 
                                   String.format("%.1f%%", (double) failedAlerts / alerts.size() * 100) + 
                                   ". 네트워크 설정 또는 채널 구성을 점검하세요.");
            }

            double avgRetryCount = alerts.stream()
                    .mapToInt(AnnouncementAlert::getRetryCount)
                    .average()
                    .orElse(0.0);
            if (avgRetryCount > 1.5) {
                recommendations.add("🔄 재시도 빈도 높음: 평균 " + String.format("%.1f", avgRetryCount) + "회. " +
                                   "채널 안정성 점검 또는 타임아웃 설정 조정이 필요합니다.");
            }
        }

        // 시간 패턴 기반 권장사항
        Map<Integer, Long> hourlyDist = detections.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getDetectedAt().getHour(),
                        Collectors.counting()
                ));
        
        Optional<Map.Entry<Integer, Long>> peakHour = hourlyDist.entrySet().stream()
                .max(Map.Entry.comparingByValue());
        
        if (peakHour.isPresent() && peakHour.get().getValue() > detections.size() * 0.3) {
            recommendations.add("📊 특정 시간대 집중: " + peakHour.get().getKey() + "시에 " + 
                              String.format("%.1f%%", (double) peakHour.get().getValue() / detections.size() * 100) + 
                              " 집중. 해당 시간대 모니터링 강화를 고려하세요.");
        }

        // 긍정적 피드백
        if (recommendations.isEmpty()) {
            recommendations.add("✅ 시스템이 안정적으로 운영되고 있습니다. 현재 성능 수준을 유지하세요.");
        }

        return recommendations;
    }

    // Helper methods
    private String getConfidenceRange(BigDecimal confidence) {
        double value = confidence.doubleValue();
        if (value >= 0.95) return "95-100%";
        if (value >= 0.85) return "85-94%";
        if (value >= 0.70) return "70-84%";
        if (value >= 0.50) return "50-69%";
        return "0-49%";
    }

    private void updateChannelStats(Map<String, ChannelStats> channelStats, String channel, boolean success) {
        channelStats.computeIfAbsent(channel, k -> new ChannelStats()).update(success);
    }

    private double calculateOverallQualityScore(QualityMetrics metrics) {
        double score = 0.0;
        int factors = 0;

        if (metrics.getAverageConfidence() > 0) {
            score += metrics.getAverageConfidence() * 30; // 30점 만점
            factors++;
        }
        if (metrics.getTargetAchievementRate() > 0) {
            score += metrics.getTargetAchievementRate() * 25; // 25점 만점
            factors++;
        }
        if (metrics.getConsistencyScore() > 0) {
            score += metrics.getConsistencyScore() * 20; // 20점 만점
            factors++;
        }
        if (metrics.getAlertReliability() > 0) {
            score += metrics.getAlertReliability() * 25; // 25점 만점
            factors++;
        }

        return factors > 0 ? score / factors * 100 : 0.0;
    }

    // Data classes for analytics results
    public static class ComprehensiveAnalyticsReport {
        private String period;
        private DetectionAnalysis detectionAnalysis;
        private AlertAnalysis alertAnalysis;
        private TimePatternAnalysis timePatternAnalysis;
        private ChannelPerformanceAnalysis channelPerformanceAnalysis;
        private QualityMetrics qualityMetrics;
        private List<String> recommendations;

        // Getters and setters
        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }

        public DetectionAnalysis getDetectionAnalysis() { return detectionAnalysis; }
        public void setDetectionAnalysis(DetectionAnalysis detectionAnalysis) { this.detectionAnalysis = detectionAnalysis; }

        public AlertAnalysis getAlertAnalysis() { return alertAnalysis; }
        public void setAlertAnalysis(AlertAnalysis alertAnalysis) { this.alertAnalysis = alertAnalysis; }

        public TimePatternAnalysis getTimePatternAnalysis() { return timePatternAnalysis; }
        public void setTimePatternAnalysis(TimePatternAnalysis timePatternAnalysis) { this.timePatternAnalysis = timePatternAnalysis; }

        public ChannelPerformanceAnalysis getChannelPerformanceAnalysis() { return channelPerformanceAnalysis; }
        public void setChannelPerformanceAnalysis(ChannelPerformanceAnalysis channelPerformanceAnalysis) { this.channelPerformanceAnalysis = channelPerformanceAnalysis; }

        public QualityMetrics getQualityMetrics() { return qualityMetrics; }
        public void setQualityMetrics(QualityMetrics qualityMetrics) { this.qualityMetrics = qualityMetrics; }

        public List<String> getRecommendations() { return recommendations; }
        public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }
    }

    public static class DetectionAnalysis {
        private int totalMessages;
        private long highConfidenceDetections;
        private double highConfidenceRate;
        private Map<String, Long> confidenceDistribution;
        private Map<String, Long> patternMatchingStats;
        private Map<String, Long> topKeywords;

        // Getters and setters
        public int getTotalMessages() { return totalMessages; }
        public void setTotalMessages(int totalMessages) { this.totalMessages = totalMessages; }

        public long getHighConfidenceDetections() { return highConfidenceDetections; }
        public void setHighConfidenceDetections(long highConfidenceDetections) { this.highConfidenceDetections = highConfidenceDetections; }

        public double getHighConfidenceRate() { return highConfidenceRate; }
        public void setHighConfidenceRate(double highConfidenceRate) { this.highConfidenceRate = highConfidenceRate; }

        public Map<String, Long> getConfidenceDistribution() { return confidenceDistribution; }
        public void setConfidenceDistribution(Map<String, Long> confidenceDistribution) { this.confidenceDistribution = confidenceDistribution; }

        public Map<String, Long> getPatternMatchingStats() { return patternMatchingStats; }
        public void setPatternMatchingStats(Map<String, Long> patternMatchingStats) { this.patternMatchingStats = patternMatchingStats; }

        public Map<String, Long> getTopKeywords() { return topKeywords; }
        public void setTopKeywords(Map<String, Long> topKeywords) { this.topKeywords = topKeywords; }
    }

    public static class AlertAnalysis {
        private int totalAlerts;
        private double successRate;
        private Map<String, Long> statusDistribution;
        private Map<String, Long> priorityDistribution;
        private double averageRetryCount;

        // Getters and setters
        public int getTotalAlerts() { return totalAlerts; }
        public void setTotalAlerts(int totalAlerts) { this.totalAlerts = totalAlerts; }

        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }

        public Map<String, Long> getStatusDistribution() { return statusDistribution; }
        public void setStatusDistribution(Map<String, Long> statusDistribution) { this.statusDistribution = statusDistribution; }

        public Map<String, Long> getPriorityDistribution() { return priorityDistribution; }
        public void setPriorityDistribution(Map<String, Long> priorityDistribution) { this.priorityDistribution = priorityDistribution; }

        public double getAverageRetryCount() { return averageRetryCount; }
        public void setAverageRetryCount(double averageRetryCount) { this.averageRetryCount = averageRetryCount; }
    }

    public static class TimePatternAnalysis {
        private Map<Integer, Long> hourlyDistribution;
        private Map<String, Long> dayOfWeekDistribution;
        private String peakHour;
        private double businessHoursRatio;

        // Getters and setters
        public Map<Integer, Long> getHourlyDistribution() { return hourlyDistribution; }
        public void setHourlyDistribution(Map<Integer, Long> hourlyDistribution) { this.hourlyDistribution = hourlyDistribution; }

        public Map<String, Long> getDayOfWeekDistribution() { return dayOfWeekDistribution; }
        public void setDayOfWeekDistribution(Map<String, Long> dayOfWeekDistribution) { this.dayOfWeekDistribution = dayOfWeekDistribution; }

        public String getPeakHour() { return peakHour; }
        public void setPeakHour(String peakHour) { this.peakHour = peakHour; }

        public double getBusinessHoursRatio() { return businessHoursRatio; }
        public void setBusinessHoursRatio(double businessHoursRatio) { this.businessHoursRatio = businessHoursRatio; }
    }

    public static class ChannelPerformanceAnalysis {
        private Map<String, ChannelStats> channelStats;
        private double multiChannelSuccessRate;

        // Getters and setters
        public Map<String, ChannelStats> getChannelStats() { return channelStats; }
        public void setChannelStats(Map<String, ChannelStats> channelStats) { this.channelStats = channelStats; }

        public double getMultiChannelSuccessRate() { return multiChannelSuccessRate; }
        public void setMultiChannelSuccessRate(double multiChannelSuccessRate) { this.multiChannelSuccessRate = multiChannelSuccessRate; }
    }

    public static class ChannelStats {
        private int totalAttempts;
        private int successfulAttempts;
        private double successRate;

        public void update(boolean success) {
            totalAttempts++;
            if (success) {
                successfulAttempts++;
            }
            successRate = totalAttempts > 0 ? (double) successfulAttempts / totalAttempts : 0.0;
        }

        // Getters and setters
        public int getTotalAttempts() { return totalAttempts; }
        public void setTotalAttempts(int totalAttempts) { this.totalAttempts = totalAttempts; }

        public int getSuccessfulAttempts() { return successfulAttempts; }
        public void setSuccessfulAttempts(int successfulAttempts) { this.successfulAttempts = successfulAttempts; }

        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
    }

    public static class QualityMetrics {
        private double averageConfidence;
        private double targetAchievementRate;
        private double consistencyScore;
        private double alertReliability;
        private double overallQualityScore;

        // Getters and setters
        public double getAverageConfidence() { return averageConfidence; }
        public void setAverageConfidence(double averageConfidence) { this.averageConfidence = averageConfidence; }

        public double getTargetAchievementRate() { return targetAchievementRate; }
        public void setTargetAchievementRate(double targetAchievementRate) { this.targetAchievementRate = targetAchievementRate; }

        public double getConsistencyScore() { return consistencyScore; }
        public void setConsistencyScore(double consistencyScore) { this.consistencyScore = consistencyScore; }

        public double getAlertReliability() { return alertReliability; }
        public void setAlertReliability(double alertReliability) { this.alertReliability = alertReliability; }

        public double getOverallQualityScore() { return overallQualityScore; }
        public void setOverallQualityScore(double overallQualityScore) { this.overallQualityScore = overallQualityScore; }
    }
}