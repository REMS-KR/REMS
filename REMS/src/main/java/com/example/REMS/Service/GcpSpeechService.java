package com.example.REMS.Service;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.speech.v1.*;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 구글 클라우드(GCP) Speech-to-Text 연동 — 기존 ClovaSpeechService 대체.
 *
 * 처리 흐름:
 *   1) 업로드 오디오(m4a/mp3/amr/wav 등)를 FLAC 16kHz mono 로 트랜스코딩(JAVE2/ffmpeg)
 *      → GCP 가 m4a/aac 를 네이티브로 못 읽기 때문에 반드시 변환한다.
 *   2) 변환 결과를 GCS 버킷에 임시 업로드 (gs://bucket/stt/uuid.flac)
 *   3) longRunningRecognize 로 화자분리(diarization) 포함 인식 (통화는 60초 초과 가능 → 비동기 필수)
 *   4) 결과를 "화자1: ...\n화자2: ..." 형태로 조립 (CLOVA 와 동일 포맷 → 다운스트림 LLM 프롬프트 호환)
 *   5) finally 에서 GCS 임시 객체 삭제
 *
 * 반환 SttResult 는 ClovaSpeechService 와 동일하게 (fullText, diarizedText) 를 제공하므로
 * CallParsingService 는 주입 대상만 바꾸면 된다.
 */
@Service
public class GcpSpeechService {

    private static final Logger logger = LoggerFactory.getLogger(GcpSpeechService.class);

    @Value("${gcp.speech.bucket}")
    private String bucket;

    @Value("${gcp.speech.language:ko-KR}")
    private String languageCode;

    // 비동기 인식 최대 대기(분). 긴 통화 대비 넉넉히.
    @Value("${gcp.speech.timeout-min:8}")
    private long timeoutMin;

    private final SpeechClient speechClient;
    private final Storage storage;

    public GcpSpeechService(SpeechClient speechClient, Storage storage) {
        this.speechClient = speechClient;
        this.storage = storage;
    }

    /** STT 결과: 전체 텍스트 + 화자 라벨이 붙은 텍스트 (ClovaSpeechService 와 동일 시그니처) */
    public record SttResult(String fullText, String diarizedText) {}

    public SttResult transcribe(MultipartFile audio) {
        File srcFile = null;
        File flacFile = null;
        String objectName = "stt/" + UUID.randomUUID() + ".flac";

        try {
            // 1) 업로드 파일을 임시 파일로 저장 (ffmpeg 는 실제 파일 경로가 필요)
            String ext = guessExt(audio.getOriginalFilename(), audio.getContentType());
            srcFile = File.createTempFile("stt-src-", ext);
            audio.transferTo(srcFile);
            logger.info("STT 입력 저장 - file={}, contentType={}, size={}KB",
                    srcFile.getName(), audio.getContentType(), audio.getSize() / 1024);

            // 2) FLAC 16kHz mono 로 트랜스코딩
            flacFile = File.createTempFile("stt-out-", ".flac");
            transcodeToFlac(srcFile, flacFile);

            // 3) GCS 업로드
            byte[] flacBytes = Files.readAllBytes(flacFile.toPath());
            storage.create(
                    BlobInfo.newBuilder(bucket, objectName).setContentType("audio/flac").build(),
                    flacBytes);
            String gcsUri = "gs://" + bucket + "/" + objectName;
            logger.info("STT 업로드 완료 - uri={}, flac={}KB", gcsUri, flacBytes.length / 1024);

            // 4) 인식 설정 (화자분리 + 자동 문장부호)
            SpeakerDiarizationConfig diarization = SpeakerDiarizationConfig.newBuilder()
                    .setEnableSpeakerDiarization(true)
                    .setMinSpeakerCount(2)   // 통화는 보통 2인
                    .setMaxSpeakerCount(3)
                    .build();

            RecognitionConfig config = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.FLAC)
                    .setSampleRateHertz(16000)
                    .setLanguageCode(languageCode)
                    .setEnableAutomaticPunctuation(true)
                    .setDiarizationConfig(diarization)
                    .setModel("latest_long")   // 긴 음성/통화에 적합
                    .build();

            RecognitionAudio recognitionAudio = RecognitionAudio.newBuilder()
                    .setUri(gcsUri)
                    .build();

            // 5) 비동기 인식 실행 + 대기
            OperationFuture<LongRunningRecognizeResponse, LongRunningRecognizeMetadata> op =
                    speechClient.longRunningRecognizeAsync(config, recognitionAudio);
            LongRunningRecognizeResponse response = op.get(timeoutMin, TimeUnit.MINUTES);

            return parse(response);

        } catch (java.util.concurrent.TimeoutException e) {
            logger.error("GCP STT 시간 초과", e);
            throw new RuntimeException("음성 인식이 시간 내에 끝나지 않았습니다. 더 짧은 녹음으로 시도해주세요.", e);
        } catch (RuntimeException e) {
            throw e; // parse() 등에서 던진 사용자 메시지는 그대로 위임(→ 컨트롤러 422)
        } catch (Exception e) {
            logger.error("GCP STT 처리 실패", e);
            throw new RuntimeException("음성 인식(STT)에 실패했습니다: " + e.getMessage(), e);
        } finally {
            // 임시 GCS 객체/로컬 파일 정리
            try { storage.delete(BlobId.of(bucket, objectName)); } catch (Exception ignore) {}
            deleteQuietly(srcFile);
            deleteQuietly(flacFile);
        }
    }

    // ----- 트랜스코딩 -----
    private void transcodeToFlac(File src, File dst) {
        try {
            AudioAttributes audioAttrs = new AudioAttributes();
            audioAttrs.setCodec("flac");
            audioAttrs.setChannels(1);           // mono (화자분리 권장)
            audioAttrs.setSamplingRate(16000);   // 16kHz

            EncodingAttributes attrs = new EncodingAttributes();
            attrs.setOutputFormat("flac");
            attrs.setAudioAttributes(audioAttrs);

            new Encoder().encode(new MultimediaObject(src), dst, attrs);
            logger.info("트랜스코딩 완료 - {}KB → {}KB(flac)",
                    src.length() / 1024, dst.length() / 1024);
        } catch (Exception e) {
            logger.error("오디오 변환 실패", e);
            throw new RuntimeException("오디오 파일을 변환하지 못했습니다. 지원되는 녹음 형식인지 확인해주세요.", e);
        }
    }

    // ----- 응답 파싱 -----
    private SttResult parse(LongRunningRecognizeResponse response) {
        List<SpeechRecognitionResult> results = response.getResultsList();

        // 전체 텍스트
        StringBuilder full = new StringBuilder();
        for (SpeechRecognitionResult r : results) {
            if (r.getAlternativesCount() > 0) {
                full.append(r.getAlternatives(0).getTranscript()).append(" ");
            }
        }
        String fullText = full.toString().trim();

        // 화자분리 텍스트 — diarization 이 켜지면 "마지막 result" 의 words 에 speakerTag 가 들어온다.
        String diarized = fullText;
        if (!results.isEmpty()) {
            SpeechRecognitionResult last = results.get(results.size() - 1);
            if (last.getAlternativesCount() > 0) {
                List<WordInfo> words = last.getAlternatives(0).getWordsList();
                boolean hasSpeaker = words.stream().anyMatch(w -> w.getSpeakerTag() > 0);
                if (!words.isEmpty() && hasSpeaker) {
                    StringBuilder sb = new StringBuilder();
                    int cur = -1;
                    for (WordInfo w : words) {
                        int tag = w.getSpeakerTag();
                        if (tag != cur) {
                            if (sb.length() > 0) sb.append("\n");
                            sb.append("화자").append(tag).append(": ");
                            cur = tag;
                        }
                        sb.append(w.getWord()).append(" ");
                    }
                    diarized = sb.toString().trim();
                }
            }
        }

        if (diarized.isBlank()) {
            logger.warn("GCP 인식 텍스트 없음 - results={}", results.size());
            throw new RuntimeException("인식된 음성이 없습니다. 녹음 파일을 확인해주세요.");
        }

        logger.info("GCP STT 완료 — 길이 {}자", diarized.length());
        return new SttResult(fullText, diarized);
    }

    // ----- 유틸 -----
    private String guessExt(String original, String contentType) {
        if (original != null) {
            int dot = original.lastIndexOf('.');
            if (dot >= 0 && dot < original.length() - 1) return original.substring(dot);
        }
        if (contentType != null) {
            String ct = contentType.toLowerCase();
            if (ct.contains("mpeg") || ct.contains("mp3")) return ".mp3";
            if (ct.contains("wav")) return ".wav";
            if (ct.contains("ogg")) return ".ogg";
            if (ct.contains("amr")) return ".amr";
            if (ct.contains("mp4") || ct.contains("m4a") || ct.contains("aac")) return ".m4a";
        }
        return ".m4a";
    }

    private void deleteQuietly(File f) {
        if (f != null) { try { Files.deleteIfExists(f.toPath()); } catch (Exception ignore) {} }
    }
}
