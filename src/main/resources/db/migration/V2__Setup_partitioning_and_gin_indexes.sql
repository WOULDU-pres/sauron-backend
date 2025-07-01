-- V2__Setup_partitioning_and_gin_indexes.sql
-- 월별 파티셔닝 및 GIN 인덱스 설정

-- pg_trgm 확장 모듈 설치 (GIN 인덱스용)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 1. 기존 테이블 백업 및 파티션 테이블로 변환
-- 먼저 임시 백업 테이블 생성
CREATE TABLE IF NOT EXISTS messages_backup AS SELECT * FROM messages;
CREATE TABLE IF NOT EXISTS alerts_backup AS SELECT * FROM alerts;

-- 기존 테이블 삭제 및 파티션 테이블로 재생성
DROP TABLE IF EXISTS alerts CASCADE;
DROP TABLE IF EXISTS messages CASCADE;

-- 2. 메시지 파티션 마스터 테이블 생성
CREATE TABLE messages (
    id BIGSERIAL,
    message_id VARCHAR(100) NOT NULL,
    device_id VARCHAR(100) NOT NULL,
    chat_room_id VARCHAR(100),
    chat_room_title VARCHAR(200),
    sender_hash VARCHAR(100),
    content_encrypted TEXT,
    content_hash VARCHAR(64),
    priority VARCHAR(20) DEFAULT 'normal',
    detected_type VARCHAR(50),
    confidence_score DOUBLE PRECISION,
    detection_status VARCHAR(20) DEFAULT 'PENDING',
    metadata TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    analyzed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- 3. 알림 파티션 마스터 테이블 생성
CREATE TABLE alerts (
    id BIGSERIAL,
    message_id BIGINT NOT NULL,
    channel VARCHAR(50) NOT NULL,
    alert_type VARCHAR(50) NOT NULL,
    recipient_hash VARCHAR(100),
    content_encrypted TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER DEFAULT 0,
    error_message TEXT,
    metadata TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    delivered_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- 4. 현재 월 및 다음 몇 개월 파티션 미리 생성
-- 현재 월 파티션
CREATE TABLE messages_current PARTITION OF messages
FOR VALUES FROM (date_trunc('month', CURRENT_DATE)) 
TO (date_trunc('month', CURRENT_DATE) + INTERVAL '1 month');

CREATE TABLE alerts_current PARTITION OF alerts
FOR VALUES FROM (date_trunc('month', CURRENT_DATE)) 
TO (date_trunc('month', CURRENT_DATE) + INTERVAL '1 month');

-- 다음 월 파티션
CREATE TABLE messages_next PARTITION OF messages
FOR VALUES FROM (date_trunc('month', CURRENT_DATE) + INTERVAL '1 month') 
TO (date_trunc('month', CURRENT_DATE) + INTERVAL '2 months');

CREATE TABLE alerts_next PARTITION OF alerts
FOR VALUES FROM (date_trunc('month', CURRENT_DATE) + INTERVAL '1 month') 
TO (date_trunc('month', CURRENT_DATE) + INTERVAL '2 months');

-- 5. 인덱스 생성
-- 기본 인덱스
CREATE INDEX idx_messages_device_id ON messages(device_id);
CREATE INDEX idx_messages_chat_room_id ON messages(chat_room_id);
CREATE INDEX idx_messages_created_at ON messages(created_at);
CREATE INDEX idx_messages_detection_status ON messages(detection_status);
CREATE UNIQUE INDEX idx_messages_message_id ON messages(message_id);

-- GIN 인덱스 (암호화된 내용 검색용)
CREATE INDEX idx_messages_content_encrypted_gin ON messages USING GIN (content_encrypted gin_trgm_ops);

-- 알림 테이블 인덱스
CREATE INDEX idx_alerts_message_id ON alerts(message_id);
CREATE INDEX idx_alerts_channel ON alerts(channel);
CREATE INDEX idx_alerts_status ON alerts(status);
CREATE INDEX idx_alerts_created_at ON alerts(created_at);
CREATE INDEX idx_alerts_content_encrypted_gin ON alerts USING GIN (content_encrypted gin_trgm_ops);

-- 6. 백업 데이터 복원
INSERT INTO messages SELECT * FROM messages_backup WHERE TRUE ON CONFLICT DO NOTHING;
INSERT INTO alerts SELECT * FROM alerts_backup WHERE TRUE ON CONFLICT DO NOTHING;

-- 7. 백업 테이블 정리
DROP TABLE IF EXISTS messages_backup;
DROP TABLE IF EXISTS alerts_backup;

-- 8. 자동 파티션 생성 함수 및 트리거 설정
CREATE OR REPLACE FUNCTION create_monthly_partitions()
RETURNS void AS $$
DECLARE
    start_date date;
    end_date date;
    partition_name text;
BEGIN
    -- 3개월 후까지 파티션 미리 생성
    FOR i IN 0..2 LOOP
        start_date := date_trunc('month', CURRENT_DATE + (i || ' months')::interval);
        end_date := start_date + INTERVAL '1 month';
        
        -- 메시지 파티션
        partition_name := 'messages_' || to_char(start_date, 'YYYY_MM');
        IF NOT EXISTS (SELECT 1 FROM pg_tables WHERE tablename = partition_name) THEN
            EXECUTE format('CREATE TABLE %I PARTITION OF messages FOR VALUES FROM (%L) TO (%L)',
                partition_name, start_date, end_date);
        END IF;
        
        -- 알림 파티션
        partition_name := 'alerts_' || to_char(start_date, 'YYYY_MM');
        IF NOT EXISTS (SELECT 1 FROM pg_tables WHERE tablename = partition_name) THEN
            EXECUTE format('CREATE TABLE %I PARTITION OF alerts FOR VALUES FROM (%L) TO (%L)',
                partition_name, start_date, end_date);
        END IF;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- 9. 파티션 자동 생성 스케줄러 설정 (매월 1일 실행)
-- 참고: 실제 운영에서는 cron job이나 스케줄러로 별도 관리 권장 