package com.example.REMS.Service;

import com.example.REMS.DTO.CallDraftDTO;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 전사 텍스트 → 구조화(건물/호실/계약자) 추출. Google Gemini 사용.
 *  · responseMimeType=application/json 으로 JSON 출력 강제.
 *  · 다른 LLM(Claude/GPT)로 바꾸려면 이 클래스의 call()만 교체하면 된다.
 *
 * application.properties:
 *   gemini.api-key=여기에_API키
 *   gemini.model=gemini-2.5-flash
 */
@Service
public class LlmExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(LlmExtractionService.class);

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;

    private final ObjectMapper om = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final RestTemplate restTemplate = buildRestTemplate();

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(10_000);
        f.setReadTimeout(60_000);
        return new RestTemplate(f);
    }

    // 추출 규칙 — 프론트의 필드/코드값과 정확히 일치시킨다.
    private static final String SYSTEM_PROMPT = """
            너는 한국 부동산 통화 내용을 구조화하는 파서다.
            아래 화자분리된 통화 전사 텍스트에서 건물/호실/계약자 정보를 추출해
            지정된 JSON 스키마로만 출력한다. 설명·마크다운·코드블록 없이 순수 JSON만 출력한다.

            [중요 규칙]
            - 통화에서 명시적으로 언급된 값만 채운다. 추측하거나 지어내지 말 것.
            - 언급되지 않은 문자열 필드는 null, 숫자 필드는 0으로 둔다.
            - 모든 금액은 '만원' 단위 정수로 변환한다.
              예) "보증금 오천만원" → 5000, "월 오십" → 50, "관리비 오만원" → 5,
                  "1억" → 10000, "1억 5천" → 15000, "전세 3억" → 30000
            - 날짜는 yyyy-MM-dd 형식. 불명확하면 null.

            [building.type 코드값] house(단독/다중), multiplex(다세대), officetel(오피스텔),
              apartment(아파트), neighborhood(근린생활시설), commercial(상가) 중 하나 또는 null.

            [dealType 코드값] sale(매매), jeonse(전세), monthly(월세) 중 하나 또는 null.
              - sale  이면 deposit=매매가, rent=0
              - jeonse 이면 deposit=전세금, rent=0
              - monthly 이면 deposit=보증금, rent=월세

            [unit.type 코드값] residential(주거), commercial(상가), office(사무) 중 하나 또는 null.
            [unit.status 코드값] empty(공실), occupied(임차중), expiring(만료임박) 중 하나. 기본 empty.

            [판단]
            - 건물 전체/여러 호실 얘기면 units[]에 각 호실을 넣는다. 호수(예:101호)가 없으면 층으로 유추.
            - 임차인/계약자 개인의 계약 상담이면 tenant 에 채운다(전화번호·건물명·호실·금액·계약기간).
            - building.units 는 항상 빈 배열로 둔다(호실은 최상위 units 에만 넣는다).

            [confidence] 각 항목을 얼마나 확신하는지 0.0~1.0. 통화에 근거가 약하면 낮게.

            [출력 스키마]
            {
              "building": {
                "name": null, "address": null, "detailAddress": null,
                "type": null, "dealType": null,
                "deposit": 0, "rent": 0, "manage": 0,
                "parkingAvailable": null, "petAllowed": null,
                "jeonseLoanAvailable": null, "jeonseLoanType": null,
                "memo": null, "units": []
              },
              "units": [
                {
                  "floor": 0, "name": null, "type": null, "status": "empty",
                  "dealType": null, "area": 0,
                  "deposit": 0, "rent": 0, "manage": 0,
                  "tenant": null, "contractStart": null, "contractEnd": null, "memo": null
                }
              ],
              "tenant": {
                "phone": null, "buildingName": null, "unitName": null,
                "deposit": 0, "rent": 0, "manage": 0,
                "contractStart": null, "contractEnd": null
              },
              "confidence": { "building": 0.0, "units": 0.0, "tenant": 0.0 }
            }
            """;

    public CallDraftDTO extract(String transcript) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + apiKey;

        Map<String, Object> reqBody = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", SYSTEM_PROMPT))),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", "다음은 통화 전사 내용이다:\n\n" + transcript))
                )),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "temperature", 0.1
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(reqBody, headers);

        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(url, request, String.class);
            JsonNode root = om.readTree(resp.getBody());
            String jsonText = root.path("candidates").path(0)
                    .path("content").path("parts").path(0).path("text").asText("");
            if (jsonText.isBlank()) {
                throw new RuntimeException("LLM이 빈 응답을 반환했습니다");
            }
            CallDraftDTO draft = om.readValue(jsonText, CallDraftDTO.class);
            logger.info("LLM 구조화 완료 — 호실 {}개", draft.getUnits() != null ? draft.getUnits().size() : 0);
            return draft;
        } catch (Exception e) {
            logger.error("LLM 추출 실패", e);
            throw new RuntimeException("통화 내용 구조화에 실패했습니다: " + e.getMessage(), e);
        }
    }
}
