package back.domain.workspace.controller;

import back.domain.workspace.dto.response.WorkspaceInfoRes;
import back.domain.workspace.dto.response.WorkspaceMemberInfoRes;
import back.domain.workspace.dto.response.WorkspaceSummaryInfoRes;
import back.domain.workspace.enums.WorkspaceMemberRole;
import back.domain.workspace.service.WorkspaceService;
import back.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class WorkspaceControllerTest {

    @Autowired private WebApplicationContext context;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private WorkspaceService workspaceService;

    private MockMvc mockMvc;
    private String accessToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        accessToken = jwtTokenProvider.generateAccessToken(1L, "test@test.com", "USER");
    }

    @Test
    @DisplayName("워크스페이스 생성 성공")
    void create_success() throws Exception {
        // given
        WorkspaceInfoRes response = new WorkspaceInfoRes(1L, "테스트", "설명", 1L,
                WorkspaceMemberRole.ADMIN, LocalDateTime.now(), LocalDateTime.now());
        given(workspaceService.create(anyLong(), any())).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"테스트\",\"description\":\"설명\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("테스트"));
    }

    @Test
    @DisplayName("워크스페이스 생성 - 인증 없을 때 401")
    void create_noAuth_returns401() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"테스트\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("내 워크스페이스 목록 조회 성공")
    void listMine_success() throws Exception {
        // given
        WorkspaceSummaryInfoRes summary = new WorkspaceSummaryInfoRes(1L, "테스트", "설명",
                WorkspaceMemberRole.ADMIN, 0, 0, LocalDateTime.now());
        given(workspaceService.listMyWorkspaces(anyLong())).willReturn(List.of(summary));

        // when & then
        mockMvc.perform(get("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("테스트"));
    }

    @Test
    @DisplayName("워크스페이스 상세 조회 성공")
    void getWorkspace_success() throws Exception {
        // given
        WorkspaceInfoRes response = new WorkspaceInfoRes(1L, "테스트", "설명", 1L,
                WorkspaceMemberRole.ADMIN, LocalDateTime.now(), LocalDateTime.now());
        given(workspaceService.getWorkspace(anyLong(), anyLong())).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/workspaces/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workspaceId").value(1L));
    }

    @Test
    @DisplayName("워크스페이스 수정 성공")
    void updateWorkspace_success() throws Exception {
        // given
        WorkspaceInfoRes response = new WorkspaceInfoRes(1L, "수정된 이름", "수정된 설명", 1L,
                WorkspaceMemberRole.ADMIN, LocalDateTime.now(), LocalDateTime.now());
        given(workspaceService.updateWorkspace(anyLong(), anyLong(), any())).willReturn(response);

        // when & then
        mockMvc.perform(patch("/api/v1/workspaces/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"수정된 이름\",\"description\":\"수정된 설명\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("수정된 이름"));
    }

    @Test
    @DisplayName("워크스페이스 멤버 목록 조회 성공")
    void listMembers_success() throws Exception {
        // given
        WorkspaceMemberInfoRes memberInfo = new WorkspaceMemberInfoRes(1L, "홍길동", "test@test.com",
                WorkspaceMemberRole.ADMIN, LocalDateTime.now());
        given(workspaceService.listMembers(anyLong(), anyLong())).willReturn(List.of(memberInfo));

        // when & then
        mockMvc.perform(get("/api/v1/workspaces/1/members")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("홍길동"));
    }

    @Test
    @DisplayName("멤버 역할 변경 성공")
    void changeMemberRole_success() throws Exception {
        // given
        doNothing().when(workspaceService).changeMemberRole(anyLong(), anyLong(), any(), anyLong());

        // when & then
        mockMvc.perform(patch("/api/v1/workspaces/1/members/2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("멤버 삭제 성공")
    void removeMember_success() throws Exception {
        // given
        doNothing().when(workspaceService).removeMember(anyLong(), anyLong(), anyLong());

        // when & then
        mockMvc.perform(delete("/api/v1/workspaces/1/members/2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }
}
