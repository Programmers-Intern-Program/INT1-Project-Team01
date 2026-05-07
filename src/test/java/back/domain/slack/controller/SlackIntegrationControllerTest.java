package back.domain.slack.controller;

import back.domain.slack.service.SlackIntegrationService;
import back.testUtil.WebMvcTestSupport;
import back.domain.slack.dto.request.SlackIntegrationCreateReq;
import back.domain.slack.dto.response.SlackIntegrationInfoRes;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import back.global.security.AuthenticatedMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(SlackIntegrationController.class)
class SlackIntegrationControllerTest extends WebMvcTestSupport {

    @MockitoBean
    private SlackIntegrationService slackIntegrationService;

    // 인증된 사용자 Security Context 생성을 위한 유틸리티 메서드
    private UsernamePasswordAuthenticationToken createTestAuthentication(Long memberId, String role) {
        AuthenticatedMember authenticatedMember = new AuthenticatedMember(memberId, role);
        return new UsernamePasswordAuthenticationToken(
                authenticatedMember, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    @DisplayName("유효한 요청이 들어오면 201 Created와 함께 마스킹된 데이터를 반환한다.")
    void createSlackIntegration_success() throws Exception {
        // given
        Long workspaceId = 1L;
        Long memberId = 100L;
        SlackIntegrationCreateReq req = new SlackIntegrationCreateReq(
                "T123", "C123", "xoxb-secret-token", "secret-key"
        );
        SlackIntegrationInfoRes mockRes = new SlackIntegrationInfoRes(
                10L, "T123", "C123", "xoxb-****oken"
        );

        given(slackIntegrationService.createSlackIntegration(eq(workspaceId), eq(memberId), any(SlackIntegrationCreateReq.class)))
                .willReturn(mockRes);

        // when & then
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/slack/integrations", workspaceId)
                        .with(csrf()) // 스프링 시큐리티 설정에 따라 필요 시 추가
                        .with(authentication(createTestAuthentication(memberId, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Slack 연동 정보가 성공적으로 등록되었습니다."))
                .andExpect(jsonPath("$.data.slackTeamId").value("T123"))
                .andExpect(jsonPath("$.data.maskedBotToken").value("xoxb-****oken"));
    }

    @Test
    @DisplayName("필수 파라미터가 누락되면 400 Bad Request를 반환한다. (Validation 검증)")
    void createSlackIntegration_validation_fail() throws Exception {
        // given
        Long workspaceId = 1L;
        Long memberId = 100L;

        // botToken이 공백인 잘못된 요청
        SlackIntegrationCreateReq req = new SlackIntegrationCreateReq(
                "T123", "C123", "", "secret-key"
        );

        // when & then
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/slack/integrations", workspaceId)
                        .with(csrf())
                        .with(authentication(createTestAuthentication(memberId, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest()); // GlobalExceptionHandler의 처리에 따라 세부 jsonPath가 달라질 수 있음
    }

    @Test
    @DisplayName("이미 존재하는 채널 연동 요청 시 409 Conflict를 반환한다.")
    void createSlackIntegration_duplicate_conflict() throws Exception {
        // given
        Long workspaceId = 1L;
        Long memberId = 100L;
        SlackIntegrationCreateReq req = new SlackIntegrationCreateReq(
                "T123", "C123", "xoxb-token", "secret"
        );

        // 서비스가 중복 예외를 던진다고 가정
        given(slackIntegrationService.createSlackIntegration(eq(workspaceId), eq(memberId), any(SlackIntegrationCreateReq.class)))
                .willThrow(new ServiceException(
                        CommonErrorCode.CONFLICT,
                        "[SlackIntegrationServiceImpl#createSlackIntegration] duplicate",
                        "해당 Slack 채널은 이미 Workspace에 연동되어 있습니다."));

        // when & then
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/slack/integrations", workspaceId)
                        .with(csrf())
                        .with(authentication(createTestAuthentication(memberId, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("해당 Slack 채널은 이미 Workspace에 연동되어 있습니다."));
    }
}