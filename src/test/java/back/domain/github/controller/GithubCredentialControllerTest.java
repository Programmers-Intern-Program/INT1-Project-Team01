package back.domain.github.controller;

import back.domain.github.dto.request.GithubCredentialCreateReq;
import back.domain.github.dto.response.GithubCredentialInfoRes;
import back.domain.github.service.GithubCredentialService;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import back.global.security.AuthenticatedMember;
import back.testUtil.WebMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GithubCredentialController.class)
class GithubCredentialControllerTest extends WebMvcTestSupport {

    @MockitoBean
    private GithubCredentialService githubCredentialService;

    private UsernamePasswordAuthenticationToken createTestAuthentication(Long memberId, String role) {
        AuthenticatedMember authenticatedMember = new AuthenticatedMember(memberId, role);
        return new UsernamePasswordAuthenticationToken(
                authenticatedMember, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    @DisplayName("유효한 GitHub PAT 등록 요청 시 201 Created와 마스킹된 토큰 응답을 반환한다.")
    void createGithubCredential_success() throws Exception {
        // given
        Long workspaceId = 1L;
        Long memberId = 100L;
        GithubCredentialCreateReq req = new GithubCredentialCreateReq(
                "My-Prod-PAT", "ghp_secrettokenstring"
        );
        GithubCredentialInfoRes mockRes = new GithubCredentialInfoRes(
                20L, "My-Prod-PAT", "ghp_****ring"
        );

        given(githubCredentialService.createGithubCredential(eq(workspaceId), eq(memberId), any(GithubCredentialCreateReq.class)))
                .willReturn(mockRes);

        // when & then
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/github/credentials", workspaceId)
                        .with(csrf())
                        .with(authentication(createTestAuthentication(memberId, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        // [Boot 4.0 변경점] jsonMapper 사용
                        .content(jsonMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("GitHub 자격 증명이 성공적으로 등록되었습니다."))
                .andExpect(jsonPath("$.data.displayName").value("My-Prod-PAT"))
                .andExpect(jsonPath("$.data.maskedToken").value("ghp_****ring"));
    }

    @Test
    @DisplayName("필수 파라미터가 누락된 요청 시 400 Bad Request를 반환한다.")
    void createGithubCredential_validation_fail() throws Exception {
        // given
        Long workspaceId = 1L;
        Long memberId = 100L;
        // token 필드가 누락된 요청
        GithubCredentialCreateReq req = new GithubCredentialCreateReq(
                "No-Token-Name", ""
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
}