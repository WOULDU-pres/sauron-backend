-- V1__Create_initial_schema.sql
-- 초기 데이터베이스 스키마 생성

-- 메시지 테이블 생성 (이미 존재할 수 있으므로 IF NOT EXISTS 사용)
CREATE TABLE IF NOT EXISTS messages (
    id BIGSERIAL PRIMARY KEY,
    message_id VARCHAR(100) NOT NULL UNIQUE,
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
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 알림 테이블 생성
CREATE TABLE IF NOT EXISTS alerts (
    id BIGSERIAL PRIMARY KEY,
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
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 기본 인덱스 생성 (이미 존재할 수 있으므로 IF NOT EXISTS 사용)
CREATE INDEX IF NOT EXISTS idx_messages_device_id ON messages(device_id);
CREATE INDEX IF NOT EXISTS idx_messages_chat_room_id ON messages(chat_room_id);
CREATE INDEX IF NOT EXISTS idx_messages_created_at ON messages(created_at);
CREATE INDEX IF NOT EXISTS idx_messages_detection_status ON messages(detection_status);

CREATE INDEX IF NOT EXISTS idx_alerts_message_id ON alerts(message_id);
CREATE INDEX IF NOT EXISTS idx_alerts_channel ON alerts(channel);
CREATE INDEX IF NOT EXISTS idx_alerts_status ON alerts(status);
CREATE INDEX IF NOT EXISTS idx_alerts_created_at ON alerts(created_at);

-- 외래 키 제약 조건 추가
ALTER TABLE alerts ADD CONSTRAINT IF NOT EXISTS fk_alerts_message_id 
    FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE; 