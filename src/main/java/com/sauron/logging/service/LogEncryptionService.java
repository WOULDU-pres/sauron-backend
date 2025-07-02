package com.sauron.logging.service;

import com.sauron.logging.dto.LogEntry;
import com.sauron.logging.entity.AuditLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 로그 암호화 서비스
 * 민감한 로그 데이터의 암호화/복호화를 담당합니다.
 */
@Service
@Slf4j
public class LogEncryptionService {
    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    
    @Value("${logging.encryption.key:}")
    private String encryptionKey;
    
    @Value("${logging.encryption.key-size:256}")
    private int keySize;
    
    private SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * 서비스 초기화 시 암호화 키 설정
     */
    public void initializeEncryption() {
        try {
            if (encryptionKey != null && !encryptionKey.trim().isEmpty()) {
                // 설정된 키 사용
                byte[] keyBytes = Base64.getDecoder().decode(encryptionKey);
                this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
                log.info("Encryption service initialized with provided key");
            } else {
                // 새 키 생성
                this.secretKey = generateSecretKey();
                log.info("Encryption service initialized with generated key");
                log.warn("No encryption key provided - generated new key (production environment should use fixed key)");
            }
        } catch (Exception e) {
            log.error("Failed to initialize encryption service", e);
            throw new EncryptionException("Failed to initialize encryption service", e);
        }
    }
    
    /**
     * LogEntry의 민감한 필드 암호화
     */
    public LogEntry encryptSensitiveFields(LogEntry logEntry) {
        try {
            if (secretKey == null) {
                initializeEncryption();
            }
            
            return LogEntry.builder()
                .id(logEntry.getId())
                .logType(logEntry.getLogType())
                .source(logEntry.getSource())
                .severity(logEntry.getSeverity())
                .message(encrypt(logEntry.getMessage())) // 메시지 암호화
                .details(encrypt(logEntry.getDetails())) // 상세 내용 암호화
                .timestamp(logEntry.getTimestamp())
                .metadata(logEntry.getMetadata()) // 메타데이터는 암호화하지 않음
                .build();
                
        } catch (Exception e) {
            log.error("Failed to encrypt log entry fields", e);
            throw new EncryptionException("Failed to encrypt log entry", e);
        }
    }
    
    /**
     * AuditLog의 민감한 필드 암호화
     */
    public AuditLog encryptSensitiveFields(AuditLog auditLog) {
        try {
            if (secretKey == null) {
                initializeEncryption();
            }
            
            return auditLog.toBuilder()
                .message(encrypt(auditLog.getMessage()))
                .details(encrypt(auditLog.getDetails()))
                .encrypted(true)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to encrypt audit log fields - ID: {}", auditLog.getId(), e);
            throw new EncryptionException("Failed to encrypt audit log", e);
        }
    }
    
    /**
     * LogEntry의 민감한 필드 복호화
     */
    public LogEntry decryptSensitiveFields(LogEntry logEntry) {
        try {
            if (secretKey == null) {
                initializeEncryption();
            }
            
            return LogEntry.builder()
                .id(logEntry.getId())
                .logType(logEntry.getLogType())
                .source(logEntry.getSource())
                .severity(logEntry.getSeverity())
                .message(decrypt(logEntry.getMessage()))
                .details(decrypt(logEntry.getDetails()))
                .timestamp(logEntry.getTimestamp())
                .metadata(logEntry.getMetadata())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to decrypt log entry fields", e);
            throw new EncryptionException("Failed to decrypt log entry", e);
        }
    }
    
    /**
     * AuditLog의 민감한 필드 복호화
     */
    public AuditLog decryptSensitiveFields(AuditLog auditLog) {
        try {
            if (secretKey == null) {
                initializeEncryption();
            }
            
            if (!auditLog.isEncrypted()) {
                return auditLog; // 암호화되지 않은 로그는 그대로 반환
            }
            
            return auditLog.toBuilder()
                .message(decrypt(auditLog.getMessage()))
                .details(decrypt(auditLog.getDetails()))
                .encrypted(false) // 복호화됨을 표시
                .build();
                
        } catch (Exception e) {
            log.error("Failed to decrypt audit log fields - ID: {}", auditLog.getId(), e);
            throw new EncryptionException("Failed to decrypt audit log", e);
        }
    }
    
    /**
     * 문자열 암호화
     */
    private String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        
        try {
            // IV 생성
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            // 암호화 수행
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);
            
            byte[] encryptedData = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            
            // IV + 암호화된 데이터를 Base64로 인코딩
            byte[] encryptedWithIv = new byte[GCM_IV_LENGTH + encryptedData.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedData, 0, encryptedWithIv, GCM_IV_LENGTH, encryptedData.length);
            
            return Base64.getEncoder().encodeToString(encryptedWithIv);
            
        } catch (Exception e) {
            log.error("Failed to encrypt text", e);
            throw new EncryptionException("Failed to encrypt text", e);
        }
    }
    
    /**
     * 문자열 복호화
     */
    private String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        
        try {
            // Base64 디코딩
            byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedText);
            
            // IV와 암호화된 데이터 분리
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedData = new byte[encryptedWithIv.length - GCM_IV_LENGTH];
            
            System.arraycopy(encryptedWithIv, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedWithIv, GCM_IV_LENGTH, encryptedData, 0, encryptedData.length);
            
            // 복호화 수행
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);
            
            byte[] decryptedData = cipher.doFinal(encryptedData);
            
            return new String(decryptedData, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("Failed to decrypt text", e);
            throw new EncryptionException("Failed to decrypt text", e);
        }
    }
    
    /**
     * 새로운 암호화 키 생성
     */
    private SecretKey generateSecretKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(keySize);
            return keyGenerator.generateKey();
        } catch (Exception e) {
            log.error("Failed to generate secret key", e);
            throw new EncryptionException("Failed to generate secret key", e);
        }
    }
    
    /**
     * 현재 암호화 키를 Base64 문자열로 반환 (백업용)
     */
    public String getEncryptionKeyAsString() {
        if (secretKey == null) {
            initializeEncryption();
        }
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }
    
    /**
     * 암호화 키 유효성 검사
     */
    public boolean validateEncryptionKey() {
        try {
            if (secretKey == null) {
                initializeEncryption();
            }
            
            // 테스트 문자열로 암호화/복호화 검증
            String testText = "encryption_test_" + System.currentTimeMillis();
            String encrypted = encrypt(testText);
            String decrypted = decrypt(encrypted);
            
            boolean isValid = testText.equals(decrypted);
            
            if (isValid) {
                log.debug("Encryption key validation successful");
            } else {
                log.error("Encryption key validation failed - decrypted text doesn't match original");
            }
            
            return isValid;
            
        } catch (Exception e) {
            log.error("Encryption key validation failed", e);
            return false;
        }
    }
    
    /**
     * 암호화 예외 클래스
     */
    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message) {
            super(message);
        }
        
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}