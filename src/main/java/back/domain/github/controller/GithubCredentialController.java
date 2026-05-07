package back.domain.github.controller;

import back.domain.github.dto.request.GithubCredentialCreateReq;
import back.domain.github.dto.response.GithubCredentialInfoRes;
import back.domain.github.service.GithubCredentialService;
import back.global.response.RsData;
import back.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/github/credentials")
@RequiredArgsConstructor
public class GithubCredentialController {

    private final GithubCredentialService githubCredentialService;

    /**
     * Workspace의 GitHub 자격 증명(PAT)을 신규 등록합니다.
     */
    @PostMapping
    public ResponseEntity<RsData<GithubCredentialInfoRes>> createGithubCredential(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @RequestBody @Valid GithubCredentialCreateReq req) {

        GithubCredentialInfoRes res = githubCredentialService.createGithubCredential(
                workspaceId,
                authenticatedMember.memberId(),
                req
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RsData<>(res, "GitHub 자격 증명이 성공적으로 등록되었습니다."));
    }
}