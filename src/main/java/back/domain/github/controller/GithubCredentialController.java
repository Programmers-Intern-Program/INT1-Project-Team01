package back.domain.github.controller;

import back.domain.github.controller.docs.GithubCredentialControllerDocs;
import back.domain.github.dto.request.GithubCredentialCreateReq;
import back.domain.github.dto.request.GithubCredentialUpdateReq;
import back.domain.github.dto.response.GithubCredentialInfoRes;
import back.domain.github.service.GithubCredentialService;
import back.global.response.RsData;
import back.global.security.AuthenticatedMember;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/github/credentials")
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2", justification = "Spring 서비스 빈은 싱글톤으로 관리되므로 안전합니다."
)
public class GithubCredentialController implements GithubCredentialControllerDocs {

    private final GithubCredentialService githubCredentialService;

    @Override
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

    @Override
    @GetMapping
    public ResponseEntity<RsData<List<GithubCredentialInfoRes>>> getGithubCredentials(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {

        List<GithubCredentialInfoRes> res = githubCredentialService.getGithubCredentials(
                workspaceId,
                authenticatedMember.memberId()
        );

        return ResponseEntity.ok(new RsData<>(res, "GitHub 자격 증명 목록을 성공적으로 조회했습니다."));
    }

    @Override
    @PatchMapping("/{credentialId}")
    public ResponseEntity<RsData<GithubCredentialInfoRes>> updateGithubCredential(
            @PathVariable Long workspaceId,
            @PathVariable Long credentialId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @RequestBody @Valid GithubCredentialUpdateReq req) {

        GithubCredentialInfoRes res = githubCredentialService.updateGithubCredential(
                workspaceId,
                credentialId,
                authenticatedMember.memberId(),
                req
        );

        return ResponseEntity.ok(new RsData<>(res, "GitHub 자격 증명이 성공적으로 수정되었습니다."));
    }

    @Override
    @DeleteMapping("/{credentialId}")
    public ResponseEntity<RsData<Void>> deleteGithubCredential(
            @PathVariable Long workspaceId,
            @PathVariable Long credentialId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {

        githubCredentialService.deleteGithubCredential(
                workspaceId,
                credentialId,
                authenticatedMember.memberId()
        );

        return ResponseEntity.ok(new RsData<>(null, "GitHub 자격 증명이 성공적으로 삭제되었습니다."));
    }
}