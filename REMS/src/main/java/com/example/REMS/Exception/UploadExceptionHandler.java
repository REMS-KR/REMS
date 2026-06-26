package com.example.REMS.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.nio.charset.StandardCharsets;

/**
 * 업로드 관련 예외를 "평문 메시지"로 내려준다.
 * 프론트의 handleResponse 가 응답 본문 텍스트(res.text())를 그대로 에러 메시지로 쓰므로,
 * JSON 이 아니라 평문으로 보내야 토스트에 사람이 읽을 수 있는 문구가 그대로 노출된다.
 * (text/plain; charset=UTF-8 로 보내 한글 깨짐 방지)
 */
@RestControllerAdvice
public class UploadExceptionHandler {

    private ResponseEntity<String> plain(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(message);
    }

    // 용량 초과: application.yml 의 max-file-size / max-request-size 초과
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleMaxSize(MaxUploadSizeExceededException e) {
        return plain(HttpStatus.PAYLOAD_TOO_LARGE,                       // 413
                "사진 용량이 너무 큽니다. 전체 20MB 이하로 첨부해 주세요.");
    }

    // 장수 초과: 최대 10장
    @ExceptionHandler(ImageLimitExceededException.class)
    public ResponseEntity<String> handleImageLimit(ImageLimitExceededException e) {
        return plain(HttpStatus.BAD_REQUEST, e.getMessage());           // 400
    }
}
