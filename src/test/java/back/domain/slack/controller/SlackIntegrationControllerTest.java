package back.domain.slack.controller;

import back.domain.slack.dto.request.SlackIntegrationCreateReq;
import back.domain.slack.dto.request.SlackIntegrationUpdateReq;
import back.domain.slack.dto.response.SlackIntegrationInfoRes;
import back.domain.slack.service.SlackIntegrationService;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import back.testUtil.WebMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SlackIntegrationController.class)
class SlackIntegrationControllerTest extends WebMvcTestSupport {

    @MockitoBean
    private SlackIntegrationService slackIntegrationService;

    @Test
    @DisplayName("유효한 연동 정보로 등록을 요청하면 201 Created를 반환한다.")
    void createSlackIntegration_success() throws Exception {
        // given
        Long workspaceId = 1L;
        Long memberId = 100L;
        SlackIntegrationCreateReq req = new SlackIntegrationCreateReq(
                "T12345", "C12345", "xoxb-real-bot-token-for-test", "secret-key"
        );

        SlackIntegrationInfoRes res = new SlackIntegrationInfoRes(
                10L, "T12345", "C12345", "xoxb-****test"
        );

        given(slackIntegrationService.createSlackIntegration(eq(workspaceId), eq(memberId), any(SlackIntegrationCreateReq.class)))
                .willReturn(res);

        // when & then
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/slack/integrations", workspaceId)
                        .with(csrf())
                        .with(authentication(createTestAuthentication(memberId, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(10L))
                .andExpect(jsonPath("$.data.slackTeamId").value("T12345"))
                .andExpect(jsonPath("$.data.maskedBotToken").value("xoxb-****test"));
    }

    @Test
    @DisplayName("필수 필드가 누락된 상태로 등록을 요청하면 400 Bad Request를 반환한다.")
    void createSlackIntegration_validation_fail() throws Exception {
        // given
        Long workspaceId = 1L;
        Long memberId = 100L;
        SlackIntegrationCreateReq req = new SlackIntegrationCreateReq(
                "", "C12345", "xoxb-token", "secret"
        );

        // when & then
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/slack/integrations", workspaceId)
                        .with(csrf())
                        .with(authentication(createTestAuthentication(memberId, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
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

    @Test
    @DisplayName("워크스페이스에 등록된 슬랙 연동 정보 목록을 조회하면 200 OK를 반환한다.")
    void getSlackIntegrations_success() throws Exception {
        // given
        Long workspaceId = 1L;
        Long memberId = 100L;
        SlackIntegrationInfoRes res = new SlackIntegrationInfoRes(10L, "T12345", "C12345", "xoxb-****test");

        given(slackIntegrationService.getSlackIntegrations(workspaceId, memberId))
                .willReturn(List.of(res));

        // when & then
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/slack/integrations", workspaceId)
                        .with(authentication(createTestAuthentication(memberId, "MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(10L))
                .andExpect(jsonPath("$.data[0].slackTeamId").value("T12345"));
    }

    @Test
    @DisplayName("슬랙 연동 정보 수정 요청 시 200 OK와 수정된 결과를 반환한다.")
    void updateSlackIntegration_success() throws Exception {
        // given
        Long workspaceId = 1L;
        Long integrationId = 10L;
        Long memberId = 100L;
        SlackIntegrationUpdateReq req = new SlackIntegrationUpdateReq(null, "C999", null, null);

        SlackIntegrationInfoRes res = new SlackIntegrationInfoRes(10L, "T12345", "C999", "xoxb-****test");

        given(slackIntegrationService.updateSlackIntegration(eq(workspaceId), eq(integrationId), eq(memberId), any(SlackIntegrationUpdateReq.class)))
                .willReturn(res);

        // when & then
        mockMvc.perform(patch("/api/v1/workspaces/{workspaceId}/slack/integrations/{integrationId}", workspaceId, integrationId)
                        .with(csrf())
                        .with(authentication(createTestAuthentication(memberId, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slackChannelId").value("C999"));
    }

    @Test
    @DisplayName("슬랙 연동 정보 삭제 요청 시 200 OK를 반환한다.")
    void deleteSlackIntegration_success() throws Exception {
        // given
        Long workspaceId = 1L;
        Long integrationId = 10L;
        Long memberId = 100L;

        doNothing().when(slackIntegrationService).deleteSlackIntegration(workspaceId, integrationId, memberId);

        // when & then
        mockMvc.perform(delete("/api/v1/workspaces/{workspaceId}/slack/integrations/{integrationId}", workspaceId, integrationId)
                        .with(csrf())
                        .with(authentication(createTestAuthentication(memberId, "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Slack 연동 정보가 성공적으로 삭제되었습니다."));
    }
}