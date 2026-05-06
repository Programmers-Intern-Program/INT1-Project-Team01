package back.domain.slack.filter;

import back.domain.slack.entity.SlackIntegration;
import back.domain.slack.repository.SlackIntegrationRepository;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import back.global.filter.RepeatableReadRequestWrapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Slack Events API(Webhook) 요청의 무결성을 검증하는 보안 필터입니다.
 * HMAC-SHA256 알고리즘을 통해 검증하며, 실패 시 ServiceException을 발생시켜 일관된 응답을 제공합니다.
 */
@Slf4j
@Component
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "JsonMapper는 Jackson이 관리하는 스레드 안전 객체이며 불변으로 사용됨")

public class SlackSignatureVerificationFilter extends OncePerRequestFilter {

    private static final String SLACK_WEBHOOK_URI = "/api/v1/slack/events";
    private static final String SLACK_SIGNATURE_HEADER = "X-Slack-Signature";
    private static final String SLACK_TIMESTAMP_HEADER = "X-Slack-Request-Timestamp";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final JsonMapper jsonMapper;
    private final SlackIntegrationRepository slackIntegrationRepository;
    private final int replayAttackThresholdSeconds;

    public SlackSignatureVerificationFilter(
            JsonMapper jsonMapper,
            SlackIntegrationRepository slackIntegrationRepository,
            @Value("${security.slack.replay-timeout-seconds}") int replayAttackThresholdSeconds) {
        this.jsonMapper = jsonMapper;
        this.slackIntegrationRepository = slackIntegrationRepository;
        this.replayAttackThresholdSeconds = replayAttackThresholdSeconds;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(SLACK_WEBHOOK_URI);
    }

    /**
     * 필터의 메인 실행 흐름입니다.
     * 내부 헬퍼 메서드들에서 검증 실패 시 ServiceException을 던지도록(Fail-Fast) 구성하였으며,
     * 여기서 catch 하여 클라이언트에게 JSON 형태로 에러를 응답합니다.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        RepeatableReadRequestWrapper wrappedRequest = wrapRequest(request);

        try {
            if (isUrlVerificationRequest(wrappedRequest.getCachedBody())) {
                log.info("Slack URL Verification 요청 - 서명 검증 skip");
                filterChain.doFilter(wrappedRequest, response);
                return;
            }

            validateHeadersAndTimestamp(wrappedRequest);
            String slackTeamId = extractTeamIdFromBody(wrappedRequest.getCachedBody());
            String plainSigningSecret = getSigningSecret(slackTeamId);
            verifySignature(wrappedRequest, plainSigningSecret);

            filterChain.doFilter(wrappedRequest, response);

        } catch (ServiceException e) {
            log.warn("Slack 서명 검증 실패 - ErrorCode: {}, 로그: {}, 클라이언트 응답: {}",
                    e.getErrorCode().resultCode(), e.getLogMessage(), e.getClientMessage());
            sendServiceExceptionResponse(response, e);
        } catch (Exception e) {
            log.error("Slack 서명 검증 중 예상치 못한 서버 에러 발생", e);
            ServiceException internalError = new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    e.getMessage(),
                    "서명 검증 중 내부 서버 오류가 발생했습니다."
            );
            sendServiceExceptionResponse(response, internalError);
        }
    }

    // Private Helper Methods ==================================================

    private RepeatableReadRequestWrapper wrapRequest(HttpServletRequest request) throws IOException {
        if (request instanceof RepeatableReadRequestWrapper) {
            return (RepeatableReadRequestWrapper) request;
        }
        return new RepeatableReadRequestWrapper(request);
    }

    /**
     * Slack이 필수로 보내야 하는 서명(Signature)과 시간(Timestamp) 헤더의 존재를 확인하고,
     * 해당 요청이 임계값 이내에 생성된 최신 요청인지 검증하여 Replay Attack을 차단합니다.
     */
    private void validateHeadersAndTimestamp(RepeatableReadRequestWrapper request) {
        String slackSignature = request.getHeader(SLACK_SIGNATURE_HEADER);
        String slackTimestamp = request.getHeader(SLACK_TIMESTAMP_HEADER);

        if (slackSignature == null || slackTimestamp == null) {
            throw new ServiceException(
                    CommonErrorCode.UNAUTHORIZED,
                    "Missing headers",
                    "Slack 서명 또는 타임스탬프 헤더가 누락되었습니다."
            );
        }

        try {
            long timestamp = Long.parseLong(slackTimestamp);
            long currentTime = Instant.now().getEpochSecond();

            if (Math.abs(currentTime - timestamp) > replayAttackThresholdSeconds) {
                throw new ServiceException(
                        CommonErrorCode.UNAUTHORIZED,
                        "Replay attack suspected",
                        "타임스탬프가 만료되었습니다. (Replay Attack 의심)"
                );
            }
        } catch (NumberFormatException e) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST,
                    "Invalid timestamp format",
                    "유효하지 않은 타임스탬프 형식입니다."
            );
        }
    }

    /**
     * HTTP 바디(JSON)를 파싱하여 최상단에 위치한 'team_id'를 추출합니다.
     * 잘못된 JSON 형식이 전달될 경우를 대비해 파싱 예외를 명시적으로 처리합니다.
     */
    private String extractTeamIdFromBody(byte[] cachedBody) {
        JsonNode rootNode;
        try {
            rootNode = jsonMapper.readTree(cachedBody);
        } catch (Exception e) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST,
                    "Invalid JSON payload: " + e.getMessage(),
                    "잘못된 JSON 페이로드 형식입니다."
            );
        }

        String teamId = rootNode.path("team_id").asString();

        if (teamId == null || teamId.isBlank()) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST,
                    "Missing team_id in payload",
                    "페이로드에 team_id가 존재하지 않습니다."
            );
        }
        return teamId;
    }

    /**
     * 추출한 team_id를 기반으로 DB에서 암호화된 Signing Secret을 조회 시,
     * Converter에 의해 복호화된 Signing Secret을 가져옵니다
     */
    private String getSigningSecret(String slackTeamId) {
        SlackIntegration integration = slackIntegrationRepository.findFirstBySlackTeamId(slackTeamId)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "Unregistered team_id: " + slackTeamId,
                        "등록되지 않은 Slack 워크스페이스입니다."
                ));

        return integration.getSigningSecret();
    }

    /**
     * 평문 Signing Secret을 열쇠(Key)로 사용하여 요청 바디의 HMAC-SHA256 해시를 직접 계산하고,
     * 이를 슬랙이 헤더로 보낸 서명과 비교합니다.
     * 타이밍 공격 방어를 위해 MessageDigest.isEqual()을 사용합니다.
     */
    private void verifySignature(RepeatableReadRequestWrapper request, String plainSigningSecret) throws Exception {
        String slackSignature = request.getHeader(SLACK_SIGNATURE_HEADER);
        String slackTimestamp = request.getHeader(SLACK_TIMESTAMP_HEADER);
        String requestBodyString = new String(request.getCachedBody(), StandardCharsets.UTF_8);

        String baseString = "v0:" + slackTimestamp + ":" + requestBodyString;

        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(plainSigningSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        byte[] hash = mac.doFinal(baseString.getBytes(StandardCharsets.UTF_8));

        String calculatedSignature = "v0=" + HexFormat.of().formatHex(hash);

        if (!MessageDigest.isEqual(slackSignature.getBytes(StandardCharsets.UTF_8), calculatedSignature.getBytes(StandardCharsets.UTF_8))) {
            throw new ServiceException(
                    CommonErrorCode.UNAUTHORIZED,
                    "Signature mismatch",
                    "서명(Signature)이 일치하지 않습니다."
            );
        }
    }

    /**
     * 프로젝트 표준 JSON 규격으로 필터 레벨의 에러 응답
     */
    private void sendServiceExceptionResponse(HttpServletResponse response, ServiceException e) throws IOException {
        response.setStatus(e.getErrorCode().statusCode());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        String jsonResponse = jsonMapper.writeValueAsString(e.getRsData());
        response.getWriter().write(jsonResponse);
    }

    private boolean isUrlVerificationRequest(byte[] cachedBody) {
        try {
            JsonNode rootNode = jsonMapper.readTree(cachedBody);
            return "url_verification".equals(rootNode.path("type").asString());
        } catch (Exception e) {
            return false;
        }
    }
}