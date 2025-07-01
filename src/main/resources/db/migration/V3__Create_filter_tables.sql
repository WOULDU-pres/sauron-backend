-- V3__Create_filter_tables.sql
-- 화이트리스트/예외 단어 관리 테이블 생성

-- 화이트리스트 단어 테이블
CREATE TABLE IF NOT EXISTS whitelist_words (
    id BIGSERIAL PRIMARY KEY,
    word VARCHAR(255) NOT NULL,
    word_type VARCHAR(50) NOT NULL DEFAULT 'GENERAL', -- GENERAL, SENDER, CONTENT_PATTERN
    description TEXT,
    is_regex BOOLEAN NOT NULL DEFAULT FALSE,
    is_case_sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    priority INTEGER NOT NULL DEFAULT 0,
    created_by VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    
    -- 중복 방지를 위한 유니크 제약조건
    CONSTRAINT unique_whitelist_word_type UNIQUE (word, word_type)
);

-- 예외 단어 테이블 (화이트리스트와 유사하지만 분리하여 관리)
CREATE TABLE IF NOT EXISTS exception_words (
    id BIGSERIAL PRIMARY KEY,
    word VARCHAR(255) NOT NULL,
    word_type VARCHAR(50) NOT NULL DEFAULT 'GENERAL', -- GENERAL, SENDER, CONTENT_PATTERN
    description TEXT,
    is_regex BOOLEAN NOT NULL DEFAULT FALSE,
    is_case_sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    priority INTEGER NOT NULL DEFAULT 0,
    exception_scope VARCHAR(50) NOT NULL DEFAULT 'ALL', -- ALL, SPAM, ADVERTISEMENT, ABUSE
    created_by VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    
    -- 중복 방지를 위한 유니크 제약조건
    CONSTRAINT unique_exception_word_type UNIQUE (word, word_type, exception_scope)
);

-- 필터 적용 이력 테이블 (모니터링 및 분석용)
CREATE TABLE IF NOT EXISTS filter_applications (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL,
    filter_type VARCHAR(50) NOT NULL, -- WHITELIST, EXCEPTION
    matched_word_id BIGINT,
    matched_word VARCHAR(255) NOT NULL,
    original_detection_type VARCHAR(50),
    final_detection_type VARCHAR(50),
    confidence_adjustment DECIMAL(3,2),
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE
);

-- 인덱스 생성 (성능 최적화)
CREATE INDEX IF NOT EXISTS idx_whitelist_words_word ON whitelist_words(word);
CREATE INDEX IF NOT EXISTS idx_whitelist_words_type ON whitelist_words(word_type);
CREATE INDEX IF NOT EXISTS idx_whitelist_words_active ON whitelist_words(is_active);
CREATE INDEX IF NOT EXISTS idx_whitelist_words_priority ON whitelist_words(priority DESC);

CREATE INDEX IF NOT EXISTS idx_exception_words_word ON exception_words(word);
CREATE INDEX IF NOT EXISTS idx_exception_words_type ON exception_words(word_type);
CREATE INDEX IF NOT EXISTS idx_exception_words_scope ON exception_words(exception_scope);
CREATE INDEX IF NOT EXISTS idx_exception_words_active ON exception_words(is_active);
CREATE INDEX IF NOT EXISTS idx_exception_words_priority ON exception_words(priority DESC);

CREATE INDEX IF NOT EXISTS idx_filter_applications_message_id ON filter_applications(message_id);
CREATE INDEX IF NOT EXISTS idx_filter_applications_filter_type ON filter_applications(filter_type);
CREATE INDEX IF NOT EXISTS idx_filter_applications_applied_at ON filter_applications(applied_at);

-- GIN 인덱스 (빠른 텍스트 검색을 위한)
CREATE INDEX IF NOT EXISTS idx_whitelist_words_word_gin ON whitelist_words USING GIN (word gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_exception_words_word_gin ON exception_words USING GIN (word gin_trgm_ops);

-- 기본 데이터 삽입
INSERT INTO whitelist_words (word, word_type, description, created_by) VALUES
('안녕하세요', 'GENERAL', '일반적인 인사말', 'SYSTEM'),
('감사합니다', 'GENERAL', '감사 표현', 'SYSTEM'),
('좋은', 'GENERAL', '긍정적 표현', 'SYSTEM'),
('공지', 'CONTENT_PATTERN', '공지사항 관련', 'SYSTEM'),
('알림', 'CONTENT_PATTERN', '알림 관련', 'SYSTEM')
ON CONFLICT (word, word_type) DO NOTHING;

INSERT INTO exception_words (word, word_type, description, exception_scope, created_by) VALUES
('무료체험', 'CONTENT_PATTERN', '무료체험은 광고가 아닌 경우가 많음', 'ADVERTISEMENT', 'SYSTEM'),
('공지사항', 'CONTENT_PATTERN', '공지사항은 정상 메시지', 'ALL', 'SYSTEM'),
('관리자', 'SENDER', '관리자 발송 메시지는 예외', 'ALL', 'SYSTEM')
ON CONFLICT (word, word_type, exception_scope) DO NOTHING; 