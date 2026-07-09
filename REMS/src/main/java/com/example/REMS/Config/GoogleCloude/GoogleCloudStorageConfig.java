package com.example.REMS.Config.GoogleCloude;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechSettings;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

// 스프링 서버가 Google Cloud(Storage 버킷 / Speech-to-Text)에 접근하기 위한 인증 설정.
//  · 기존 storage() 빈은 그대로 두고, STT용 speechClient() 빈만 추가했다.
//  · 같은 서비스계정 키(google-cloud-service.json)를 재사용한다.
@Configuration
public class GoogleCloudStorageConfig {

    // 공통 인증 정보 로드 — cloud-platform 스코프(Storage + Speech 모두 포함)
    private GoogleCredentials loadCredentials() throws IOException {
        return GoogleCredentials
                .fromStream(new ClassPathResource("google-cloud-service.json").getInputStream())
                .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
    }

    @Bean
    public Storage storage() throws IOException {
        return StorageOptions.newBuilder()
                .setCredentials(loadCredentials())
                .build()
                .getService();
    }

    // Speech-to-Text 클라이언트 — AutoCloseable 이라 컨텍스트 종료 시 Spring 이 close() 호출
    @Bean(destroyMethod = "close")
    public SpeechClient speechClient() throws IOException {
        SpeechSettings settings = SpeechSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(loadCredentials()))
                .build();
        return SpeechClient.create(settings);
    }
}
