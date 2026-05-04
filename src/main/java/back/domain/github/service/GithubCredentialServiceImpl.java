package back.domain.github.service;

import back.domain.github.dto.request.GithubCredentialCreateReq;
import back.domain.github.dto.response.GithubCredentialInfoRes;
import back.domain.github.entity.GithubCredential;
import back.domain.github.repository.GithubCredentialRepository;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GithubCredentialServiceImpl implements GithubCredentialService {

    private final GithubCredentialRepository githubCredentialRepository;

    @Override
    @Transactional
    public GithubCredentialInfoRes createGithubCredential(Long workspaceId, Long memberId, GithubCredentialCreateReq req) {

        // TODO: Workspace 검증 로직 호출 (ADMIN인지 확인) [IT-9]
        // 예: workspaceValidator.checkWorkspaceAdminPermission(workspaceId, memberId);

        if (githubCredentialRepository.existsByWorkspaceIdAndDisplayName(workspaceId, req.displayName())) {
            throw new ServiceException(
                    CommonErrorCode.CONFLICT,
                    "[GithubCredentialServiceImpl#createGithubCredential] Duplicate displayName: " + req.displayName(),
                    "해당 이름의 GitHub 자격 증명이 이미 존재합니다."
            );
        }

        GithubCredential credential = GithubCredential.builder()
                .workspaceId(workspaceId)
                .displayName(req.displayName())
                .token(req.token())
                .createdByMemberId(memberId)
                .build();

        GithubCredential savedCredential = githubCredentialRepository.save(credential);

        return GithubCredentialInfoRes.from(savedCredential);
    }
}