-- Migration for Announcement Detection Tables
-- T-007: 공지/이벤트 메시지 자동 감지 및 별도 알림 기능 구현

-- 공지 패턴 관리 테이블
CREATE TABLE announcement_patterns (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    regex_pattern TEXT NOT NULL,
    confidence_weight DECIMAL(3,2) NOT NULL CHECK (confidence_weight >= 0.0 AND confidence_weight <= 1.0),
    active BOOLEAN DEFAULT true,
    category VARCHAR(50) DEFAULT 'GENERAL',
    priority INTEGER DEFAULT 1 CHECK (priority >= 1 AND priority <= 10),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 공지 감지 결과 저장 테이블
CREATE TABLE announcement_detections (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT REFERENCES messages(id) ON DELETE CASCADE,
    pattern_matched VARCHAR(100),
    confidence_score DECIMAL(3,2) NOT NULL,
    time_factor DECIMAL(3,2),
    keywords_matched TEXT, -- Comma-separated matched keywords
    special_chars_found TEXT, -- Special characters detected
    time_expressions TEXT, -- Comma-separated time expressions
    detected_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    alert_sent BOOLEAN DEFAULT false,
    alert_type VARCHAR(50)
);

-- 공지 알림 히스토리 테이블
CREATE TABLE announcement_alerts (
    id BIGSERIAL PRIMARY KEY,
    detection_id BIGINT REFERENCES announcement_detections(id) ON DELETE CASCADE,
    alert_type VARCHAR(50) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    message_content TEXT NOT NULL,
    recipient VARCHAR(100),
    sent_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    delivery_status VARCHAR(20) DEFAULT 'PENDING' CHECK (delivery_status IN ('PENDING', 'SENT', 'FAILED', 'DELIVERED')),
    retry_count INTEGER DEFAULT 0,
    error_message TEXT
);

-- 인덱스 생성
CREATE INDEX idx_announcement_patterns_active ON announcement_patterns(active);
CREATE INDEX idx_announcement_patterns_category ON announcement_patterns(category);
CREATE INDEX idx_announcement_detections_message_id ON announcement_detections(message_id);
CREATE INDEX idx_announcement_detections_detected_at ON announcement_detections(detected_at);
CREATE INDEX idx_announcement_detections_alert_sent ON announcement_detections(alert_sent);
CREATE INDEX idx_announcement_alerts_detection_id ON announcement_alerts(detection_id);
CREATE INDEX idx_announcement_alerts_sent_at ON announcement_alerts(sent_at);
CREATE INDEX idx_announcement_alerts_delivery_status ON announcement_alerts(delivery_status);

-- 기본 공지 패턴 데이터 삽입
INSERT INTO announcement_patterns (name, description, regex_pattern, confidence_weight, category, priority) VALUES
-- 일반 공지 패턴
('공지사항', '일반적인 공지사항 키워드', '공지|공고|알림|안내|공시|발표', 0.8, 'GENERAL', 5),
('이벤트 공지', '이벤트 관련 공지', '이벤트|행사|축제|대회|컨테스트|콘테스트', 0.9, 'EVENT', 6),
('시간 관련 공지', '시간이 포함된 공지', '\\d{1,2}[시:]\\d{0,2}[분]?|\\d{1,2}월\\s*\\d{1,2}일|오늘|내일|이번주|다음주', 0.7, 'SCHEDULE', 7),
('중요 알림', '중요도가 높은 알림', '중요|긴급|필수|반드시|꼭|중요한|긴급한', 0.9, 'URGENT', 9),
('모집 공고', '인원 모집 관련 공고', '모집|신청|참가|등록|접수|지원|신청자|참가자', 0.8, 'RECRUITMENT', 5),

-- 특수 문자 패턴
('공지 장식 문자', '공지에 자주 사용되는 특수문자', '[★☆■□▶▷●○◆◇※◈◇▲△▼▽]', 0.6, 'DECORATION', 3),
('강조 기호', '강조를 위한 기호들', '[!]{2,}|[?]{2,}|[~]{2,}|[-]{3,}|[=]{3,}', 0.5, 'EMPHASIS', 2),

-- 시간 표현 패턴
('정확한 시간', '정확한 시간 표현', '\\d{1,2}:\\d{2}|\\d{1,2}시\\s*\\d{0,2}분?|\\d{1,2}시반', 0.8, 'TIME_EXACT', 8),
('날짜 표현', '날짜 관련 표현', '\\d{1,2}/\\d{1,2}|\\d{4}-\\d{1,2}-\\d{1,2}|\\d{1,2}월\\s*\\d{1,2}일', 0.7, 'DATE', 6),
('상대적 시간', '상대적 시간 표현', '오늘|내일|모레|어제|이번주|다음주|이번달|다음달', 0.6, 'TIME_RELATIVE', 4),

-- 제외 패턴 (negative weight)
('스팸 제외', '스팸으로 분류될 가능성이 높은 키워드', '스팸|광고|홍보|마케팅|판매|구매|할인쿠폰', -0.5, 'EXCLUSION', 1),
('일상 대화 제외', '일상적인 대화 표현', '^안녕|^하이|^헬로|ㅋㅋ|ㅎㅎ|ㅠㅠ|^^', -0.3, 'CASUAL', 1);

-- 공지 감지 통계 뷰 생성
CREATE VIEW announcement_detection_stats AS
SELECT 
    DATE(detected_at) as detection_date,
    COUNT(*) as total_detections,
    COUNT(CASE WHEN alert_sent = true THEN 1 END) as alerts_sent,
    AVG(confidence_score) as avg_confidence,
    string_agg(DISTINCT pattern_matched, ', ') as patterns_used
FROM announcement_detections
WHERE detected_at >= CURRENT_DATE - INTERVAL '30 days'
GROUP BY DATE(detected_at)
ORDER BY detection_date DESC;

-- 공지 알림 성능 통계 뷰 생성
CREATE VIEW announcement_alert_performance AS
SELECT 
    channel,
    alert_type,
    COUNT(*) as total_alerts,
    COUNT(CASE WHEN delivery_status = 'DELIVERED' THEN 1 END) as successful_deliveries,
    COUNT(CASE WHEN delivery_status = 'FAILED' THEN 1 END) as failed_deliveries,
    ROUND(
        COUNT(CASE WHEN delivery_status = 'DELIVERED' THEN 1 END) * 100.0 / COUNT(*), 2
    ) as success_rate,
    AVG(EXTRACT(EPOCH FROM (sent_at - (SELECT detected_at FROM announcement_detections WHERE id = announcement_alerts.detection_id)))) as avg_response_time_seconds
FROM announcement_alerts
WHERE sent_at >= CURRENT_DATE - INTERVAL '7 days'
GROUP BY channel, alert_type
ORDER BY success_rate DESC;