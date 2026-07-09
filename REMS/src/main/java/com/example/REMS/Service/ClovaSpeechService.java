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
            String original = audio.getOriginalFilename();
            final String filename = ensureAudioFilename(original, audio.getContentType());
            fileResource = new ByteArrayResource(audio.getBytes()) {
                @Override public String getFilename() { return filename; }
            };
            logger.info("STT 업로드 - filename={}, contentType={}, size={}KB",
                    filename, audio.getContentType(), audio.getSize() / 1024);
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
        } catch (org.springframework.web.client.RestClientResponseException e) {
            // CLOVA 가 4xx/5xx 로 응답한 경우 — 실제 원인(쿼터/인증/도메인 등)이 본문에 담겨 옴
            logger.error("CLOVA Speech HTTP 오류 - status={}, body={}",
                    e.getStatusCode(), truncate(e.getResponseBodyAsString(), 1500));
            throw new RuntimeException("음성 인식(STT) 요청이 거부되었습니다 (HTTP "
                    + e.getStatusCode().value() + "). CLOVA 도메인/시크릿/사용량을 확인해주세요.", e);
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
                // CLOVA 가 200 을 주면서도 인식 결과가 없을 때 — 실제 원인(쿼터/포맷/무음 등)을
                // 알 수 있도록 CLOVA 응답의 상태/메시지와 원문 일부를 로그로 남긴다.
                String result = root.path("result").asText("");
                String message = root.path("message").asText("");
                logger.warn("CLOVA 인식 텍스트 없음 - result='{}', message='{}', 응답원문(일부)={}",
                        result, message, truncate(responseBody, 1500));
                String detail = (!result.isBlank() || !message.isBlank())
                        ? " (CLOVA: " + result + (message.isBlank() ? "" : " / " + message) + ")"
                        : "";
                throw new RuntimeException("인식된 음성이 없습니다. 녹음 파일을 확인해주세요." + detail);
            }
            logger.info("STT 완료 — 길이 {}자", diarized.length());
            return new SttResult(fullText, diarized);
        } catch (Exception e) {
            throw new RuntimeException("STT 응답 파싱 실패: " + e.getMessage(), e);
        }
    }

    // 파일명에 오디오 확장자가 없으면 contentType 으로 보정 (CLOVA 가 확장자로 포맷을 판별함)
    private String ensureAudioFilename(String original, String contentType) {
        String name = (original != null && !original.isBlank()) ? original : "call-audio";
        if (name.matches("(?i).*\\.(m4a|mp3|wav|amr|aac|ogg|flac|mp4)$")) return name;
        String ext = ".m4a";
        if (contentType != null) {
            String ct = contentType.toLowerCase();
            if (ct.contains("mpeg") || ct.contains("mp3")) ext = ".mp3";
            else if (ct.contains("wav")) ext = ".wav";
            else if (ct.contains("ogg")) ext = ".ogg";
            else if (ct.contains("aac")) ext = ".aac";
            else if (ct.contains("amr")) ext = ".amr";
            else if (ct.contains("mp4") || ct.contains("m4a") || ct.contains("aac")) ext = ".m4a";
        }
        return name + ext;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…(총 " + s.length() + "자)";
    }
}
