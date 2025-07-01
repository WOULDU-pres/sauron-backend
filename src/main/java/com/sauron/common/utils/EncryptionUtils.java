package com.sauron.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 암호화/비식별화 유틸리티
 * AES-256-GCM 암호화와 SHA-256 해싱을 제공합니다.
 * 메시지 내용과 개인정보를 안전하게 처리합니다.
 */
@Component
@Slf4j
public class EncryptionUtils {
    
    private static final String AES_ALGORITHM = "AES";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int GCM_IV_LENGTH = 12; // 96비트
    private static final int GCM_TAG_LENGTH = 16; // 128비트
    
    private final SecretKey encryptionKey;
    private final String saltPrefix;
    
    /**
     * 암호화 키와 솔트 초기화
     */
    public EncryptionUtils(@Value("${sauron.encryption.key:}") String base64Key,
                          @Value("${sauron.encryption.salt:sauron-salt-2024}") String saltPrefix) {
        this.saltPrefix = saltPrefix;
        this.encryptionKey = initializeEncryptionKey(base64Key);
        
        log.info("EncryptionUtils initialized with algorithm: {}", AES_TRANSFORMATION);
    }
    
    /**
     * 텍스트 암호화 (AES-256-GCM)
     * 
     * @param plainText 암호화할 텍스트
     * @return Base64 인코딩된 암호화 결과 (IV + 암호화된 데이터)
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        
        try {
            // IV 생성 (Initialization Vector)
            byte[] iv = generateRandomIV();
            
            // Cipher 초기화
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, parameterSpec);
            
            // 암호화 수행
            byte[] encryptedData = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            
            // IV + 암호화된 데이터 결합
            byte[] encryptedWithIv = new byte[GCM_IV_LENGTH + encryptedData.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedData, 0, encryptedWithIv, GCM_IV_LENGTH, encryptedData.length);
            
            // Base64 인코딩하여 반환
            String result = Base64.getEncoder().encodeToString(encryptedWithIv);
            
            log.debug("Text encrypted successfully - Original length: {}, Encrypted length: {}", 
                     plainText.length(), result.length());
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to encrypt text - Length: {}", plainText.length(), e);
            throw new EncryptionException("Encryption failed", e);
        }
    }
    
    /**
     * 텍스트 복호화 (AES-256-GCM)
     * 
     * @param encryptedText Base64 인코딩된 암호화 텍스트
     * @return 복호화된 원본 텍스트
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        
        try {
            // Base64 디코딩
            byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedText);
            
            // IV와 암호화된 데이터 분리
            if (encryptedWithIv.length < GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Encrypted data too short");
            }
            
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedData = new byte[encryptedWithIv.length - GCM_IV_LENGTH];
            
            System.arraycopy(encryptedWithIv, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedWithIv, GCM_IV_LENGTH, encryptedData, 0, encryptedData.length);
            
            // Cipher 초기화
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, parameterSpec);
            
            // 복호화 수행
            byte[] decryptedData = cipher.doFinal(encryptedData);
            String result = new String(decryptedData, StandardCharsets.UTF_8);
            
            log.debug("Text decrypted successfully - Encrypted length: {}, Decrypted length: {}", 
                     encryptedText.length(), result.length());
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to decrypt text - Length: {}", encryptedText.length(), e);
            throw new EncryptionException("Decryption failed", e);
        }
    }
    
    /**
     * 텍스트 해싱 (SHA-256)
     * 
     * @param text 해싱할 텍스트
     * @return Hex 인코딩된 해시 값
     */
    public String hash(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            
            // 솔트 추가 (Rainbow table 공격 방지)
            String saltedText = saltPrefix + text;
            byte[] hashBytes = digest.digest(saltedText.getBytes(StandardCharsets.UTF_8));
            
            // Hex 인코딩
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            String result = hexString.toString();
            
            log.debug("Text hashed successfully - Original length: {}, Hash length: {}", 
                     text.length(), result.length());
            
            return result;
            
        } catch (NoSuchAlgorithmException e) {
            log.error("Hashing algorithm not available: {}", HASH_ALGORITHM, e);
            throw new EncryptionException("Hashing failed", e);
        }
    }
    
    /**
     * 발신자 정보 해싱 (비식별화)
     * 
     * @param senderInfo 발신자 정보
     * @return 해싱된 발신자 정보
     */
    public String hashSender(String senderInfo) {
        if (senderInfo == null || senderInfo.isEmpty()) {
            return senderInfo;
        }
        
        String prefixedSender = "sender:" + senderInfo;
        return hash(prefixedSender);
    }
    
    /**
     * 채팅방 정보 해싱 (비식별화)
     * 
     * @param chatRoomInfo 채팅방 정보
     * @return 해싱된 채팅방 정보
     */
    public String hashChatRoom(String chatRoomInfo) {
        if (chatRoomInfo == null || chatRoomInfo.isEmpty()) {
            return chatRoomInfo;
        }
        
        String prefixedChatRoom = "room:" + chatRoomInfo;
        return hash(prefixedChatRoom);
    }
    
    /**
     * 콘텐츠 해싱 (중복 검출용)
     * 
     * @param content 메시지 콘텐츠
     * @return 해싱된 콘텐츠
     */
    public String hashContent(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        String prefixedContent = "content:" + content;
        return hash(prefixedContent);
    }
    
    /**
     * 채팅방 제목 익명화
     * 
     * @param title 원본 채팅방 제목
     * @return 익명화된 제목
     */
    public String anonymizeChatRoomTitle(String title) {
        if (title == null || title.isEmpty()) {
            return title;
        }
        
        // 길이에 따른 익명화 처리
        if (title.length() <= 3) {
            return "***";
        } else if (title.length() <= 10) {
            return title.substring(0, 3) + "***";
        } else {
            return title.substring(0, 5) + "***" + title.substring(title.length() - 2);
        }
    }
    
    /**
     * 암호화 키 검증
     * 
     * @return 키가 유효한지 여부
     */
    public boolean isKeyValid() {
        try {
            String testText = "test_encryption_key_validation";
            String encrypted = encrypt(testText);
            String decrypted = decrypt(encrypted);
            
            boolean isValid = testText.equals(decrypted);
            
            log.debug("Encryption key validation: {}", isValid ? "PASSED" : "FAILED");
            
            return isValid;
            
        } catch (Exception e) {
            log.error("Encryption key validation failed", e);
            return false;
        }
    }
    
    /**
     * 새로운 암호화 키 생성
     * 
     * @return Base64 인코딩된 새 키
     */
    public static String generateNewKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(AES_ALGORITHM);
            keyGenerator.init(256); // AES-256
            
            SecretKey secretKey = keyGenerator.generateKey();
            String base64Key = Base64.getEncoder().encodeToString(secretKey.getEncoded());
            
            log.info("New encryption key generated - Length: {} bits", secretKey.getEncoded().length * 8);
            
            return base64Key;
            
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to generate new encryption key", e);
            throw new EncryptionException("Key generation failed", e);
        }
    }
    
    /**
     * 암호화 키 초기화
     */
    private SecretKey initializeEncryptionKey(String base64Key) {
        try {
            byte[] keyBytes;
            
            if (base64Key == null || base64Key.trim().isEmpty()) {
                log.warn("No encryption key provided, generating temporary key");
                // 임시 키 생성 (운영 환경에서는 반드시 환경변수로 설정 필요)
                KeyGenerator keyGenerator = KeyGenerator.getInstance(AES_ALGORITHM);
                keyGenerator.init(256);
                keyBytes = keyGenerator.generateKey().getEncoded();
            } else {
                keyBytes = Base64.getDecoder().decode(base64Key);
            }
            
            if (keyBytes.length != 32) { // 256비트 = 32바이트
                throw new IllegalArgumentException("Invalid key length: expected 32 bytes for AES-256");
            }
            
            return new SecretKeySpec(keyBytes, AES_ALGORITHM);
            
        } catch (Exception e) {
            log.error("Failed to initialize encryption key", e);
            throw new EncryptionException("Key initialization failed", e);
        }
    }
    
    /**
     * 랜덤 IV 생성
     */
    private byte[] generateRandomIV() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
    
    /**
     * 암호화 관련 예외
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