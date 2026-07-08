package com.example.REMS.Service;

import com.example.REMS.DTO.AiFilterCriteria;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 제미나이(Gemini) 호출 전용 서비스.
 *
 * 역할은 딱 하나: "사용자 자연어 → 구조화된 필터(AiFilterCriteria) 추출".
 * 매물 필터링 자체는 하지 않는다(그건 AiSearchService 가 코드로 정확히 처리).
 *
 * 정확도 확보 장치
 *  - temperature = 0        : 매번 같은 입력엔 같은 출력(결정론적)
 *  - responseMimeType JSON  : 자유 문장 없이 JSON 만 반환
 *  - responseSchema         : 필드/타입을 스키마로 강제 → 파싱 실패 최소화
 *  - 프롬프트에서 단위(만원)·코드값·"언급 안 되면 null" 규칙을 명시
 *
 * 설정은 application.yml 참고:
 *   gemini.api-key : ${GEMINI_API_KEY}
 *   gemini.model   : gemini-2.5-flash
 */
@Service
public class GeminiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    private final ObjectMapper mapper = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    /**
     * 자연어 검색 문장을 필터 조건으로 변환한다.
     * @param userQuery 예) "강남구 보증금 5천 이하 주차되는 월세"
     * @return 추출된 필터 (실패 시 예외)
     */
    public AiFilterCriteria parseQuery(String userQuery) {
        if (userQuery == null || userQuery.trim().isEmpty()) {
            throw new IllegalArgumentException("검색어가 비어 있습니다.");
        }

        try {
            String body = buildRequestBody(userQuery.trim());
            String url = String.format(ENDPOINT, model, apiKey);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logger.error("Gemini 응답 오류 status={} body={}", response.statusCode(), response.body());
                throw new RuntimeException("AI 요청이 실패했습니다 (status " + response.statusCode() + ")");
            }

            String extractedJson = extractText(response.body());
            AiFilterCriteria criteria = mapper.readValue(extractedJson, AiFilterCriteria.class);
            logger.info("AI 필터 추출 완료: query=\"{}\" → {}", userQuery, extractedJson);
            return criteria;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Gemini 호출 중 예외", e);
            throw new RuntimeException("AI 검색 처리 중 오류가 발생했습니다.", e);
        }
    }

    // ===== 요청 본문 구성 =====
    private String buildRequestBody(String userQuery) throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();

        // contents: system 지침 + 사용자 질의
        Map<String, Object> part = Map.of("text", buildPrompt(userQuery));
        Map<String, Object> content = Map.of("role", "user", "parts", List.of(part));
        root.put("contents", List.of(content));

        // generationConfig: JSON 강제 + 스키마 + 결정론
        Map<String, Object> genConfig = new LinkedHashMap<>();
        genConfig.put("temperature", 0);
        genConfig.put("responseMimeType", "application/json");
        genConfig.put("responseSchema", buildResponseSchema());
        root.put("generationConfig", genConfig);

        return mapper.writeValueAsString(root);
    }

    // 사용자에게 보이지 않는 시스템 지침(프롬프트)
    private String buildPrompt(String userQuery) {
        return String.join("\n",
            "당신은 한국 부동산 매물 검색 필터 추출기입니다.",
            "사용자의 자연어 요청을 읽고, 아래 규칙에 맞춰 JSON 필터만 출력하세요.",
            "설명 문장이나 마크다운 없이 JSON 객체만 반환합니다.",
            "",
            "[규칙]",
            "1) 금액 단위는 모두 '만원' 정수입니다.",
            "   - '1억' = 10000, '5천'·'5천만원' = 5000, '3억5천' = 35000, '500' = 500.",
            "2) dealType: 매매→\"sale\", 전세→\"jeonse\", 월세→\"monthly\". 언급 없으면 null.",
            "3) type: 단독/다중주택→\"house\", 다세대/빌라→\"multiplex\", 오피스텔→\"officetel\",",
            "   아파트→\"apartment\", 근린생활시설→\"neighborhood\", 상가→\"commercial\". 언급 없으면 null.",
            "4) 금액 범위: '이하/미만/까지'는 Max, '이상/초과/부터'는 Min 에 넣습니다.",
            "   보증금·전세금·매매가는 depositMin/depositMax, 월세는 rentMin/rentMax, 관리비는 manageMax.",
            "5) location: 구/동/역/지역/건물명 등 주소에 들어갈 핵심 키워드 하나. 예) \"강남구\", \"역삼동\", \"판교역\".",
            "6) 옵션: 주차 요구→parking=true, 애완/반려 가능 요구→pet=true, 전세대출 가능 요구→jeonseLoan=true,",
            "   공실/빈 방만 요구→onlyVacant=true. 요구하지 않았으면 null (false 로 채우지 말 것).",
            "7) 면적은 ㎡ 기준(areaMin/areaMax). '평'으로 말하면 1평=3.3058㎡ 로 환산해 ㎡ 값으로 넣습니다.",
            "8) keyword: 위 항목으로 못 담는 부가 요구사항 문자열(선택). 없으면 null.",
            "9) 문장에 명시되지 않은 필드는 반드시 null 로 두세요. 임의로 값을 만들지 마세요.",
            "",
            "[사용자 요청]",
            userQuery
        );
    }

    // Gemini responseSchema (OpenAPI 서브셋). 모든 필드 nullable.
    private Map<String, Object> buildResponseSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("dealType",   enumOrNull("sale", "jeonse", "monthly"));
        props.put("type",       enumOrNull("house", "multiplex", "officetel", "apartment", "neighborhood", "commercial"));
        props.put("depositMin", typeNullable("INTEGER"));
        props.put("depositMax", typeNullable("INTEGER"));
        props.put("rentMin",    typeNullable("INTEGER"));
        props.put("rentMax",    typeNullable("INTEGER"));
        props.put("manageMax",  typeNullable("INTEGER"));
        props.put("location",   typeNullable("STRING"));
        props.put("parking",    typeNullable("BOOLEAN"));
        props.put("pet",        typeNullable("BOOLEAN"));
        props.put("jeonseLoan", typeNullable("BOOLEAN"));
        props.put("onlyVacant", typeNullable("BOOLEAN"));
        props.put("areaMin",    typeNullable("NUMBER"));
        props.put("areaMax",    typeNullable("NUMBER"));
        props.put("keyword",    typeNullable("STRING"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "OBJECT");
        schema.put("properties", props);
        return schema;
    }

    private Map<String, Object> typeNullable(String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("nullable", true);
        return m;
    }

    private Map<String, Object> enumOrNull(String... values) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "STRING");
        m.put("nullable", true);
        m.put("enum", List.of(values));
        return m;
    }

    // ===== 응답 파싱: candidates[0].content.parts[0].text =====
    private String extractText(String responseBody) throws Exception {
        JsonNode root = mapper.readTree(responseBody);
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            // 안전 필터 차단 등으로 후보가 없을 때
            String reason = root.path("promptFeedback").path("blockReason").asText("");
            throw new RuntimeException("AI가 응답을 생성하지 못했습니다."
                    + (reason.isEmpty() ? "" : " (" + reason + ")"));
        }
        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            throw new RuntimeException("AI 응답 형식이 올바르지 않습니다.");
        }
        String text = parts.get(0).path("text").asText("");
        if (text.isBlank()) {
            throw new RuntimeException("AI 응답이 비어 있습니다.");
        }
        return text.trim();
    }
}
