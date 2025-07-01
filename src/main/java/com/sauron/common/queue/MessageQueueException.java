package com.sauron.common.queue;

/**
 * 메시지 큐 처리 중 발생하는 예외
 */
public class MessageQueueException extends RuntimeException {
    
    public MessageQueueException(String message) {
        super(message);
    }
    
    public MessageQueueException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public MessageQueueException(Throwable cause) {
        super(cause);
    }
} 