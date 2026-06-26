package com.example.REMS.Exception;

// 첨부 이미지 장수 한도(예: 10장) 초과 시 발생
public class ImageLimitExceededException extends RuntimeException {
    public ImageLimitExceededException(String message) {
        super(message);
    }
}
