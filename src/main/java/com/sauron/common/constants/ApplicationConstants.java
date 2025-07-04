package com.sauron.common.constants;

/**
 * 애플리케이션 전역 상수 정의
 * Spring Boot 백엔드에서 사용되는 모든 상수들을 중앙집중적으로 관리합니다.
 */
public final class ApplicationConstants {
    
    private ApplicationConstants() {
        // 유틸리티 클래스이므로 인스턴스 생성 방지
    }
    
    // =================================================================
    // API 관련 상수
    // =================================================================
    public static final class Api {
        public static final String BASE_PATH = "/api/v1";
        public static final String VERSION = "v1";
        
        public static final class Endpoints {
            public static final String MESSAGES = "/messages";
            public static final String ALERTS = "/alerts";
            public static final String ROOMS = "/rooms";
            public static final String ADMIN = "/admin";
            public static final String AUTH = "/auth";
            public static final String HEALTH = "/actuator/health";
            public static final String METRICS = "/actuator/metrics";
            public static final String OPERATIONS = "/operations";
            public static final String WHITELIST = "/whitelist";
            public static final String DETECTION = "/detection";
        }
        
        public static final class Headers {
            public static final String AUTHORIZATION = "Authorization";
            public static final String CONTENT_TYPE = "Content-Type";
            public static final String X_REQUEST_ID = "X-Request-ID";
            public static final String X_CLIENT_VERSION = "X-Client-Version";
            public static final String X_DEVICE_ID = "X-Device-ID";
            public static final String X_FORWARDED_FOR = "X-Forwarded-For";
        }
        
        public static final class Timeouts {
            public static final int DEFAULT_MS = 10000;
            public static final int UPLOAD_MS = 30000;
            public static final int ANALYSIS_MS = 60000;
            public static final int GEMINI_API_MS = 30000;
            public static final int TELEGRAM_API_MS = 15000;
        }
    }
    
    // =================================================================
    // 메시지 관련 상수
    // =================================================================
    public static final class Message {
        public static final class Types {
            public static final String NORMAL = "normal";
            public static final String ADVERTISEMENT = "advertisement";
            public static final String SPAM = "spam";
            public static final String ABUSE = "abuse";
            public static final String INAPPROPRIATE = "inappropriate";
            public static final String CONFLICT = "conflict";
            public static final String ANNOUNCEMENT = "announcement";
        }
        
        public static final class Priority {
            public static final String LOW = "low";
            public static final String NORMAL = "normal";
            public static final String HIGH = "high";
            public static final String URGENT = "urgent";
        }
        
        public static final class Status {
            public static final String PENDING = "pending";
            public static final String PROCESSING = "processing";
            public static final String ANALYZED = "analyzed";
            public static final String ALERTED = "alerted";
            public static final String ARCHIVED = "archived";
        }
        
        public static final class Limits {
            public static final int MAX_CONTENT_LENGTH = 10000;
            public static final int MAX_SENDER_NAME_LENGTH = 100;
            public static final int MAX_CHAT_TITLE_LENGTH = 200;
            public static final int MIN_HASH_LENGTH = 8;
            public static final int MAX_HASH_LENGTH = 64;
            public static final double MIN_CONFIDENCE_SCORE = 0.0;
            public static final double MAX_CONFIDENCE_SCORE = 1.0;
        }
    }
    
    // =================================================================
    // 알림 관련 상수
    // =================================================================
    public static final class Alert {
        public static final class Channels {
            public static final String TELEGRAM = "telegram";
            public static final String DISCORD = "discord";
            public static final String EMAIL = "email";
            public static final String CONSOLE = "console";
            public static final String WEBHOOK = "webhook";
            public static final String KAKAO_TALK = "kakao_talk";
        }
        
        public static final class Status {
            public static final String PENDING = "pending";
            public static final String SENT = "sent";
            public static final String FAILED = "failed";
            public static final String RETRYING = "retrying";
        }
        
        public static final class Retry {
            public static final int MAX_ATTEMPTS = 3;
            public static final long INITIAL_DELAY_MS = 1000L;
            public static final long MAX_DELAY_MS = 30000L;
            public static final double BACKOFF_MULTIPLIER = 2.0;
        }
        
        public static final class Templates {
            public static final String SPAM_DETECTED = "🚨 스팸 메시지 감지: {chatRoom}\n내용: {content}\n신뢰도: {confidence}%";
            public static final String ABUSE_DETECTED = "⚠️ 욕설/분쟁 메시지 감지: {chatRoom}\n내용: {content}\n신뢰도: {confidence}%";
            public static final String ANNOUNCEMENT_DETECTED = "📢 공지사항 감지: {chatRoom}\n내용: {content}";
        }
    }
    
    // =================================================================
    // 인증 관련 상수
    // =================================================================
    public static final class Auth {
        public static final class Roles {
            public static final String SUPER_ADMIN = "super_admin";
            public static final String ADMIN = "admin";
            public static final String MODERATOR = "moderator";
            public static final String USER = "user";
        }
        
        public static final class Token {
            public static final String TYPE = "Bearer";
            public static final long EXPIRY_TIME_MS = 24 * 60 * 60 * 1000L; // 24시간
            public static final long REFRESH_THRESHOLD_MS = 5 * 60 * 1000L; // 5분
            public static final String ISSUER = "sauron-backend";
            public static final String AUDIENCE = "sauron-client";
        }
        
        public static final class Permissions {
            public static final String READ_MESSAGES = "read:messages";
            public static final String WRITE_MESSAGES = "write:messages";
            public static final String DELETE_MESSAGES = "delete:messages";
            public static final String MANAGE_ALERTS = "manage:alerts";
            public static final String MANAGE_USERS = "manage:users";
            public static final String SYSTEM_CONFIG = "system:config";
            public static final String VIEW_OPERATIONS = "view:operations";
            public static final String MANAGE_WHITELIST = "manage:whitelist";
        }
    }
    
    // =================================================================
    // 에러 코드 상수
    // =================================================================
    public static final class ErrorCodes {
        // 일반 에러
        public static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";
        public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
        public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
        
        // 인증/인가 에러
        public static final String UNAUTHORIZED = "UNAUTHORIZED";
        public static final String FORBIDDEN = "FORBIDDEN";
        public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
        public static final String INVALID_TOKEN = "INVALID_TOKEN";
        
        // 검증 에러
        public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
        public static final String INVALID_INPUT = "INVALID_INPUT";
        public static final String MISSING_REQUIRED_FIELD = "MISSING_REQUIRED_FIELD";
        public static final String INVALID_FORMAT = "INVALID_FORMAT";
        
        // 비즈니스 로직 에러
        public static final String MESSAGE_NOT_FOUND = "MESSAGE_NOT_FOUND";
        public static final String ROOM_NOT_FOUND = "ROOM_NOT_FOUND";
        public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
        public static final String DUPLICATE_ENTRY = "DUPLICATE_ENTRY";
        
        // 외부 서비스 에러
        public static final String GEMINI_API_ERROR = "GEMINI_API_ERROR";
        public static final String TELEGRAM_API_ERROR = "TELEGRAM_API_ERROR";
        public static final String DATABASE_ERROR = "DATABASE_ERROR";
        public static final String REDIS_ERROR = "REDIS_ERROR";
        
        // 리소스 제한 에러
        public static final String RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";
        public static final String QUOTA_EXCEEDED = "QUOTA_EXCEEDED";
        public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
    }
    
    // =================================================================
    // HTTP 상태 코드
    // =================================================================
    public static final class HttpStatus {
        public static final int OK = 200;
        public static final int CREATED = 201;
        public static final int NO_CONTENT = 204;
        public static final int BAD_REQUEST = 400;
        public static final int UNAUTHORIZED = 401;
        public static final int FORBIDDEN = 403;
        public static final int NOT_FOUND = 404;
        public static final int CONFLICT = 409;
        public static final int UNPROCESSABLE_ENTITY = 422;
        public static final int TOO_MANY_REQUESTS = 429;
        public static final int INTERNAL_SERVER_ERROR = 500;
        public static final int BAD_GATEWAY = 502;
        public static final int SERVICE_UNAVAILABLE = 503;
        public static final int GATEWAY_TIMEOUT = 504;
    }
    
    // =================================================================
    // 환경 설정 상수
    // =================================================================
    public static final class Environment {
        public static final String DEVELOPMENT = "development";
        public static final String STAGING = "staging";
        public static final String PRODUCTION = "production";
        public static final String TEST = "test";
    }
    
    // =================================================================
    // 보안 관련 상수
    // =================================================================
    public static final class Security {
        public static final class Encryption {
            public static final String ALGORITHM = "AES-256-GCM";
            public static final int KEY_LENGTH = 32;
            public static final int IV_LENGTH = 16;
            public static final int TAG_LENGTH = 16;
        }
        
        public static final class Hashing {
            public static final String ALGORITHM = "SHA-256";
            public static final int SALT_ROUNDS = 12;
            public static final int PEPPER_LENGTH = 32;
        }
        
        public static final class RateLimit {
            public static final long WINDOW_MS = 60 * 1000L; // 1분
            public static final int MAX_REQUESTS = 60;
            public static final boolean SKIP_SUCCESSFUL_REQUESTS = false;
        }
    }
    
    // =================================================================
    // 시간 관련 상수
    // =================================================================
    public static final class Time {
        public static final class Milliseconds {
            public static final long SECOND = 1000L;
            public static final long MINUTE = 60 * SECOND;
            public static final long HOUR = 60 * MINUTE;
            public static final long DAY = 24 * HOUR;
            public static final long WEEK = 7 * DAY;
        }
        
        public static final class Formats {
            public static final String ISO_DATE = "yyyy-MM-dd";
            public static final String ISO_DATETIME = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
            public static final String DISPLAY_DATE = "MMM dd, yyyy";
            public static final String DISPLAY_TIME = "HH:mm:ss";
            public static final String DISPLAY_DATETIME = "MMM dd, yyyy HH:mm";
            public static final String KOREAN_DATE = "yyyy년 MM월 dd일";
            public static final String KOREAN_DATETIME = "yyyy년 MM월 dd일 HH시 mm분";
        }
        
        public static final class Timezone {
            public static final String UTC = "UTC";
            public static final String SEOUL = "Asia/Seoul";
            public static final String TOKYO = "Asia/Tokyo";
        }
    }
    
    // =================================================================
    // 파일 및 미디어 상수
    // =================================================================
    public static final class File {
        public static final class Types {
            public static final String[] IMAGE = {"jpg", "jpeg", "png", "gif", "webp"};
            public static final String[] DOCUMENT = {"pdf", "doc", "docx", "txt", "csv"};
            public static final String[] AUDIO = {"mp3", "wav", "ogg", "m4a"};
            public static final String[] VIDEO = {"mp4", "avi", "mov", "webm"};
        }
        
        public static final class Limits {
            public static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024L; // 5MB
            public static final long MAX_DOCUMENT_SIZE = 10 * 1024 * 1024L; // 10MB
            public static final long MAX_AUDIO_SIZE = 20 * 1024 * 1024L; // 20MB
            public static final long MAX_VIDEO_SIZE = 100 * 1024 * 1024L; // 100MB
        }
    }
    
    // =================================================================
    // 정규 표현식 상수
    // =================================================================
    public static final class Regex {
        public static final String EMAIL = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
        public static final String PHONE = "^\\+?[1-9]\\d{1,14}$";
        public static final String UUID = "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";
        public static final String SLUG = "^[a-z0-9]+(?:-[a-z0-9]+)*$";
        public static final String PASSWORD = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$";
        public static final String KOREAN = "^[가-힣\\s]+$";
        public static final String ALPHANUMERIC = "^[a-zA-Z0-9]+$";
        public static final String KAKAO_PACKAGE = "^com\\.kakao\\.talk$";
    }
    
    // =================================================================
    // 캐시 관련 상수
    // =================================================================
    public static final class Cache {
        public static final class Keys {
            public static final String MESSAGE_ANALYSIS = "message:analysis:";
            public static final String USER_SESSION = "user:session:";
            public static final String RATE_LIMIT = "rate:limit:";
            public static final String GEMINI_RESULT = "gemini:result:";
            public static final String WHITELIST = "whitelist:";
        }
        
        public static final class TTL {
            public static final long SHORT_SECONDS = 300L; // 5분
            public static final long MEDIUM_SECONDS = 1800L; // 30분
            public static final long LONG_SECONDS = 3600L; // 1시간
            public static final long ANALYSIS_RESULT_SECONDS = 1800L; // 30분
            public static final long USER_SESSION_SECONDS = 86400L; // 24시간
        }
    }
    
    // =================================================================
    // 큐 관련 상수
    // =================================================================
    public static final class Queue {
        public static final class Names {
            public static final String MESSAGE_ANALYSIS = "message.analysis";
            public static final String ALERT_DISPATCH = "alert.dispatch";
            public static final String LOG_PROCESSING = "log.processing";
        }
        
        public static final class Exchange {
            public static final String DIRECT = "sauron.direct";
            public static final String TOPIC = "sauron.topic";
            public static final String FANOUT = "sauron.fanout";
        }
        
        public static final class RoutingKey {
            public static final String ANALYSIS_REQUEST = "analysis.request";
            public static final String ALERT_URGENT = "alert.urgent";
            public static final String ALERT_NORMAL = "alert.normal";
        }
    }
    
    // =================================================================
    // 메트릭 관련 상수
    // =================================================================
    public static final class Metrics {
        public static final class Names {
            public static final String MESSAGE_RECEIVED = "sauron.message.received";
            public static final String MESSAGE_ANALYZED = "sauron.message.analyzed";
            public static final String ALERT_SENT = "sauron.alert.sent";
            public static final String API_REQUEST_DURATION = "sauron.api.request.duration";
            public static final String GEMINI_API_CALLS = "sauron.gemini.api.calls";
            public static final String TELEGRAM_API_CALLS = "sauron.telegram.api.calls";
        }
        
        public static final class Tags {
            public static final String ENVIRONMENT = "environment";
            public static final String MESSAGE_TYPE = "message_type";
            public static final String ALERT_CHANNEL = "alert_channel";
            public static final String API_ENDPOINT = "api_endpoint";
            public static final String ERROR_CODE = "error_code";
        }
    }
}