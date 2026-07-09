package com.example.REMS.Config;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechSettings;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * GCP 공용 빈 설정.
 *  · 서비스계정 JSON 키(src/main/resources 안의 파일)를 classpath 로 읽어 인증 정보를 만든다.
 *    → jar 로 패키징돼도 File 경로가 아니라 InputStream 으로 읽으므로 정상 동작.
 *  · Storage(버킷 업로드용) / SpeechClient(STT용) 를 싱글턴 빈으로 재사용한다.
 *
 * application.properties 예시:
 *   gcp.project-id=my-gcp-project
 *   gcp.credentials.location=classpath:my-service-account.json
 */
@Configuration
public class GcpConfig {

    @Value("${gcp.credentials.location}")
    private Resource credentialsResource;

    @Value("${gcp.project-id}")
    private String projectId;

    @Bean
    public GoogleCredentials googleCredentials() throws IOException {
        try (InputStream in = credentialsResource.getInputStream()) {
            return GoogleCredentials.fromStream(in)
                    .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
        }
    }

    @Bean
    public Storage storage(GoogleCredentials credentials) {
        return StorageOptions.newBuilder()
                .setCredentials(credentials)
                .setProjectId(projectId)
                .build()
                .getService();
    }

    // AutoCloseable 이므로 컨텍스트 종료 시 Spring 이 close() 를 호출한다.
    @Bean(destroyMethod = "close")
    public SpeechClient speechClient(GoogleCredentials credentials) throws IOException {
        SpeechSettings settings = SpeechSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build();
        return SpeechClient.create(settings);
    }
}
