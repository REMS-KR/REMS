package com.example.REMS.Service;

import com.example.REMS.DTO.CallDraftDTO;
import com.example.REMS.DTO.CustomerDTO;
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
 * 전사 텍스트 -> 구조화 추출. Google Gemini 사용.
 *  - extract()         : 건물/호실/계약자(임차인) 초안
 *  - extractCustomer() : 고객(리드) 초안 (이름/요약/전화/금액/입주희망일/위치/대출/미팅날짜/메모)
 *
 *  ※ 입력은 "화자1: ...\n화자2: ..." 형태의 화자분리 전사 텍스트(GCP STT 결과)를 그대로 받는다.
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

    // ===== 계약자(임차인)/건물 추출 프롬프트 =====
    private static final String SYSTEM_PROMPT = """
            너는 한국 부동산 통화 내용을 구조화하는 파서다.
            아래 화자분리된 통화 전사 텍스트에서 건물/호실/계약자 정보를 추출해
            지정된 JSON 스키마로만 출력한다. 설명 마크다운 코드블록 없이 순수 JSON만 출력한다.

            [중요 규칙]
            - 통화에서 명시적으로 언급된 값만 채운다. 추측하거나 지어내지 말 것.
            - 언급되지 않은 문자열 필드는 null, 숫자 필드는 0으로 둔다.
            - 모든 금액은 '만원' 단위 정수로 변환한다.
              예) "보증금 오천만원" -> 5000, "월 오십" -> 50, "1억" -> 10000, "1억 5천" -> 15000
            - 날짜는 yyyy-MM-dd 형식. 불명확하면 null.
            - tenant.name(이름): 계약자(임차인) 본인의 성함. 통화에서 언급되면 채운다
              (예: "김철수입니다", "성함이 어떻게 되세요? 박영희요"). 화자분리 라벨(화자1/화자2)에
              현혹되지 말고, 중개사 본인 이름이나 건물명·지역명은 절대 이름에 넣지 않는다. 불명확하면 null.

            [building.type 코드값] house(단독/다중), multiplex(다세대), officetel(오피스텔),
              apartment(아파트), neighborhood(근린생활시설), commercial(상가) 중 하나 또는 null.
            [dealType 코드값] sale(매매), jeonse(전세), monthly(월세) 중 하나 또는 null.
              - sale 이면 deposit=매매가, rent=0 / jeonse 이면 deposit=전세금, rent=0 / monthly 이면 deposit=보증금, rent=월세
            [unit.type 코드값] residential(주거), commercial(상가), office(사무) 중 하나 또는 null.
            [unit.status 코드값] empty(공실), occupied(임차중), expiring(만료임박) 중 하나. 기본 empty.

            [판단]
            - 건물 전체/여러 호실 얘기면 units[]에 각 호실을 넣는다. building.units 는 항상 빈 배열.
            - 임차인/계약자 개인의 계약 상담이면 tenant 에 채운다.
            [confidence] 각 항목을 얼마나 확신하는지 0.0~1.0.

            [출력 스키마]
            {
              "building": { "name": null, "address": null, "detailAddress": null, "type": null, "dealType": null,
                "deposit": 0, "rent": 0, "manage": 0, "parkingAvailable": null, "petAllowed": null,
                "jeonseLoanAvailable": null, "jeonseLoanType": null, "memo": null, "units": [] },
              "units": [ { "floor": 0, "name": null, "type": null, "status": "empty", "dealType": null, "area": 0,
                "deposit": 0, "rent": 0, "manage": 0, "tenant": null, "contractStart": null, "contractEnd": null, "memo": null } ],
              "tenant": { "name": null, "phone": null, "buildingName": null, "unitName": null, "deposit": 0, "rent": 0, "manage": 0,
                "contractStart": null, "contractEnd": null },
              "confidence": { "building": 0.0, "units": 0.0, "tenant": 0.0 }
            }
            """;

    // ===== 고객(리드) 추출 프롬프트 =====
    private static final String CUSTOMER_PROMPT = """
            너는 한국 부동산 중개사의 '고객(잠재고객) 상담 통화'를 정리하는 파서다.
            아래 화자분리된 통화 전사 텍스트에서 고객 정보를 추출해 지정된 JSON 스키마로만 출력한다.
            설명 마크다운 코드블록 없이 순수 JSON만 출력한다.

            [중요 규칙]
            - 통화에서 명시적으로 언급된 값만 채운다. 추측하거나 지어내지 말 것.
            - 언급되지 않은 필드는 null.
            - name(이름): 고객 본인의 성함. 통화에서 언급되면 채운다
              (예: "김민수입니다", "성함이요? 박서준이요"). 화자분리 라벨(화자1/화자2)에 현혹되지 말고,
              중개사 본인 이름은 절대 넣지 않는다. 없으면 null.
            - summary(요약): 고객의 니즈를 2~3문장으로 자연스럽게 한국어로 요약한다.
            - phone(전화번호): 언급된 연락처. 없으면 null.
            - amount(금액): 예산/희망 금액을 사람이 읽기 쉬운 문자열로. 예) "보증금 5000/월 50", "매매 3억", "전세 2억".
            - moveInDate(입주희망일): yyyy-MM-dd. 자유 표현이면 그대로(예: "3월 초"). 없으면 null.
            - location(위치): 희망 지역/동네.
            - loan(대출): 대출 관련 언급(예: "버팀목 대출 희망", "대출 필요 없음", "가능 여부 문의").
            - meetingDate(미팅날짜): 방문/미팅 약속일. yyyy-MM-dd 또는 자유 표현. 없으면 null.
            - memo(메모): 위에 안 들어가는 특이사항/요청.
            - sensitivity(감도)는 절대 채우지 말고 항상 null 로 둔다. (중개사가 직접 수기 체크하는 항목)

            [출력 스키마]
            {
              "name": null, "summary": null, "phone": null, "amount": null, "moveInDate": null,
              "location": null, "loan": null, "meetingDate": null, "memo": null, "sensitivity": null
            }
            """;

    public CallDraftDTO extract(String transcript) {
        String jsonText = call(SYSTEM_PROMPT, transcript);
        try {
            CallDraftDTO draft = om.readValue(jsonText, CallDraftDTO.class);
            logger.info("LLM(계약자) 구조화 완료 - 호실 {}개",
                    draft.getUnits() != null ? draft.getUnits().size() : 0);
            return draft;
        } catch (Exception e) {
            throw new RuntimeException("통화 내용 구조화에 실패했습니다: " + e.getMessage(), e);
        }
    }

    public CustomerDTO extractCustomer(String transcript) {
        String jsonText = call(CUSTOMER_PROMPT, transcript);
        try {
            CustomerDTO draft = om.readValue(jsonText, CustomerDTO.class);
            draft.setSensitivity(null); // 감도는 항상 수기 체크
            logger.info("LLM(고객) 구조화 완료");
            return draft;
        } catch (Exception e) {
            throw new RuntimeException("고객 통화 구조화에 실패했습니다: " + e.getMessage(), e);
        }
    }

    // Gemini 호출 공통부 - JSON 텍스트 반환
    private String call(String systemPrompt, String transcript) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + apiKey;

        Map<String, Object> reqBody = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
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
            if (jsonText.isBlank()) throw new RuntimeException("LLM이 빈 응답을 반환했습니다");
            return jsonText;
        } catch (Exception e) {
            logger.error("LLM 호출 실패", e);
            throw new RuntimeException("AI 처리에 실패했습니다: " + e.getMessage(), e);
        }
    }
}
