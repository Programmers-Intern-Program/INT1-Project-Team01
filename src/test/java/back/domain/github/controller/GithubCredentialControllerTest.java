package back.domain.github.controller;

import back.domain.github.dto.request.GithubCredentialCreateReq;
import back.domain.github.dto.request.GithubCredentialUpdateReq;
import back.domain.github.dto.response.GithubCredentialInfoRes;
import back.domain.github.service.GithubCredentialService;
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

@WebMvcTest(GithubCredentialController.class)
class GithubCredentialControllerTest extends WebMvcTestSupport {

    @MockitoBean
    private GithubCredentialService githubCredentialService;

    @Test
    @DisplayName("유효한 PAT 정보로 등록을 요청하면 201 Created를 반환한다.")
    void createGithubCredential_success() throws Exception {
        // given
        Long workspaceId = 1L;
        Long memberId = 100L;
        GithubCredentialCreateReq req = new GithubCredentialCreateReq(
                "Main-Repo-Access", "ghp_realtoken1234567890"
        );

        GithubCredentialInfoRes res = new GithubCredentialInfoRes(
                10L, "Main-Repo-Access", "ghp_****7890"
        );

        given(githubCredentialService.createGithubCredential(eq(workspaceId), eq(memberId), any(GithubCredentialCreateReq.class)))
                .willReturn(res);

        // when & then
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/github/credentials", workspaceId)
                        .with(csrf())
                        .with(authentication(createTestAuthentication(memberId, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(10L))
                .andExpect(jsonPath("$.data.displayName").value("Main-Repo-Access"))
                .andExpect(jsonPath("$.data.maskedToken").value("ghp_****7890"));
    }

    @Test
    @DisplayName("필수 필드가 누락된 상태로 등록을 요청하면 400 Bad Request를 반환한다.")
    void createGithubCredential_validation_fail() throws Exception {
        // given
        Long workspaceId = 1L;
        Long memberId = 100L;
        GithubCredentialCreateReq req = new GithubCredentialCreateReq(
                "", "ghp_realtoken1234567890"
        );

        // when & then
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/github/credentials", workspaceId)
                        .with(csrf())
                        .with(authentication(createTestAuthentication(memberId, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이미 존재하는 이름으로 자격 증명 등록 시 409 Conflict를 반환한다.")
    void createGithubCredential_duplicate_conflict() throws Exception {
        // given
        Long workspaceId = 1L;
        Long memberId = 100L;
        GithubCredentialCreateReq req = new GithubCredentialCreateReq(
                "Duplicated-Name", "ghp_token"
        );

        given(githubCredentialService.createGithubCredential(eq(workspaceId), eq(memberId), any(GithubCredentialCreateReq.class)))
                .willThrow(new ServiceException(
                        CommonErrorCode.CONFLICT,
                        "[GithubCredentialServiceImpl#createGithubCredential] Duplicate displayName",
                        "해당 이름의 GitHub 자격 증명이 이미 존재합니다."));

        // when & then
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/github/credentials", workspaceId)
                        .with(csrf())
                        .with(authentication(createTestAuthentication(memberId, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("해당 이름의 GitHub 자격 증명이 이미 존재합니다."));
    }

    @Test
    @DisplayName("워크스페이스에 등록된 GitHub 자격 증명 목록을 조회하면 200 OK를 반환한다.")
    void getGithubCredentials_success() throws Exception {
        // given
        Long workspaceId = 1L;
        Long memberId = 100L;
        GithubCredentialInfoRes res = new GithubCredentialInfoRes(10L, "Main-Repo-Access", "ghp_****7890");

        given(githubCredentialService.getGithubCredentials(workspaceId, memberId))
                .willReturn(List.of(res));

        // when & then
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/github/credentials", workspaceId)
                        .with(authentication(createTestAuthentication(memberId, "MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(10L))
                .andExpect(jsonPath("$.data[0].displayName").value("Main-Repo-Access"));
    }

    @Test
    @DisplayName("GitHub 자격 증명 수정 요청 시 200 OK와 수정된 결과를 반환한다.")
    void updateGithubCredential_success() throws Exception {
        // given
        Long workspaceId = 1L;
        Long credentialId = 10L;
        Long memberId = 100L;
        GithubCredentialUpdateReq req = new GithubCredentialUpdateReq("Updated-Name", null);

        GithubCredentialInfoRes res = new GithubCredentialInfoRes(10L, "Updated-Name", "ghp_****7890");

        given(githubCredentialService.updateGithubCredential(eq(workspaceId), eq(credentialId), eq(memberId), any(GithubCredentialUpdateReq.class)))
                .willReturn(res);

        // when & then
        mockMvc.perform(patch("/api/v1/workspaces/{workspaceId}/github/credentials/{credentialId}", workspaceId, credentialId)
                        .with(csrf())
                        .with(authentication(createTestAuthentication(memberId, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Updated-Name"));
    }

    @Test
    @DisplayName("GitHub 자격 증명 삭제 요청 시 200 OK를 반환한다.")
    void deleteGithubCredential_success() throws Exception {
        // given
        Long workspaceId = 1L;
        Long credentialId = 10L;
        Long memberId = 100L;

        doNothing().when(githubCredentialService).deleteGithubCredential(workspaceId, credentialId, memberId);

        // when & then
        mockMvc.perform(delete("/api/v1/workspaces/{workspaceId}/github/credentials/{credentialId}", workspaceId, credentialId)
                        .with(csrf())
                        .with(authentication(createTestAuthentication(memberId, "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("GitHub 자격 증명이 성공적으로 삭제되었습니다."));
    }
}