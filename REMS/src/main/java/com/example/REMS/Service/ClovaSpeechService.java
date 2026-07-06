package com.example.REMS.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 네이버 클라우드 CLOVA Speech(긴 문장 인식) 연동.
 *  · 업로드 방식 + 화자분리(diarization) 사용 → 통화 녹음처럼 2명 이상 대화에 적합.
 *  · NCP 콘솔에서 CLOVA Speech 도메인을 만들면 발급되는 Invoke URL / Secret Key 필요.
 *
 * application.properties:
 *   clova.speech.invoke-url=https://clovaspeech-gw.ncloud.com/external/v1/XXXX/xxxxxxxx
 *   clova.speech.secret=여기에_시크릿키
 */
@Service
public class ClovaSpeechService {

    private static final Logger logger = LoggerFactory.getLogger(ClovaSpeechService.class);

    @Value("${clova.speech.invoke-url}")
    private String invokeUrl;

    @Value("${clova.speech.secret}")
    private String secret;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // STT는 시간이 걸릴 수 있으므로 read timeout을 넉넉히(2분) 준다.
    private final RestTemplate restTemplate = buildRestTemplate();

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(10_000);
        f.setReadTimeout(120_000);
        return new RestTemplate(f);
    }

    /** STT 결과: 전체 텍스트 + 화자 라벨이 붙은 텍스트 */
    public record SttResult(String fullText, String diarizedText) {}

    public SttResult transcribe(MultipartFile audio) {
        String url = invokeUrl + "/recognizer/upload";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-CLOVASPEECH-API-KEY", secret);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        // 인식 옵션: 한국어 / 동기 응답 / 화자분리 / 전체 텍스트
        String params = "{"
                + "\"language\":\"ko-KR\","
                + "\"completion\":\"sync\","
                + "\"wordAlignment\":true,"
                + "\"fullText\":true,"
                + "\"diarization\":{\"enable\":true}"
                + "}";

        ByteArrayResource fileResource;
        try {
            final String filename = (audio.getOriginalFilename() != null && !audio.getOriginalFilename().isBlank())
                    ? audio.getOriginalFilename() : "call-audio";
            fileResource = new ByteArrayResource(audio.getBytes()) {
                @Override public String getFilename() { return filename; }
            };
        } catch (IOException e) {
            throw new RuntimeException("오디오 파일을 읽지 못했습니다: " + e.getMessage(), e);
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("media", fileResource);   // 오디오 파일 파트
        body.add("params", params);        // 옵션(JSON 문자열) 파트

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(url, request, String.class);
            return parse(resp.getBody());
        } catch (Exception e) {
            logger.error("CLOVA Speech 호출 실패", e);
            throw new RuntimeException("음성 인식(STT)에 실패했습니다: " + e.getMessage(), e);
        }
    }

    private SttResult parse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String fullText = root.path("text").asText("");

            // segments[].speaker.label 로 화자 구분 → "화자1: ...", "화자2: ..." 형태로 조립
            StringBuilder sb = new StringBuilder();
            for (JsonNode seg : root.path("segments")) {
                JsonNode sp = seg.path("speaker");
                String label = sp.path("label").asText(sp.path("name").asText(""));
                String text = seg.path("text").asText("");
                if (text.isBlank()) continue;
                if (!label.isBlank()) sb.append("화자").append(label).append(": ");
                sb.append(text).append("\n");
            }

            String diarized = sb.length() > 0 ? sb.toString().trim() : fullText;
            if (diarized.isBlank()) {
                throw new RuntimeException("인식된 음성이 없습니다. 녹음 파일을 확인해주세요.");
            }
            logger.info("STT 완료 — 길이 {}자", diarized.length());
            return new SttResult(fullText, diarized);
        } catch (Exception e) {
            throw new RuntimeException("STT 응답 파싱 실패: " + e.getMessage(), e);
        }
    }
}
