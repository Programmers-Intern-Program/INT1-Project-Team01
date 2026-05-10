package back.domain.github.service;

import back.domain.github.dto.request.GithubCredentialCreateReq;
import back.domain.github.dto.request.GithubCredentialUpdateReq;
import back.domain.github.dto.response.GithubCredentialInfoRes;
import back.domain.github.entity.GithubCredential;
import back.domain.github.repository.GithubCredentialRepository;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.repository.WorkspaceRepository;
import back.domain.workspace.service.WorkspaceAccessValidator;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GithubCredentialServiceImpl implements GithubCredentialService {

    private final GithubCredentialRepository githubCredentialRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceAccessValidator workspaceAccessValidator;

    @Override
    @Transactional
    public GithubCredentialInfoRes createGithubCredential(Long workspaceId, Long memberId, GithubCredentialCreateReq req) {

        workspaceAccessValidator.requireAdmin(workspaceId, memberId);

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "[GithubCredentialServiceImpl#createGithubCredential] workspace not found by id: " + workspaceId,
                        "워크스페이스가 존재하지 않습니다."
                ));

        if (githubCredentialRepository.existsByWorkspaceIdAndDisplayName(workspaceId, req.displayName())) {
            throw new ServiceException(
                    CommonErrorCode.CONFLICT,
                    "[GithubCredentialServiceImpl#createGithubCredential] Duplicate displayName: " + req.displayName(),
                    "해당 이름의 GitHub 자격 증명이 이미 존재합니다."
            );
        }

        GithubCredential credential = GithubCredential.builder()
                .workspace(workspace)
                .displayName(req.displayName())
                .token(req.token())
                .createdByMemberId(memberId)
                .build();

        GithubCredential savedCredential = githubCredentialRepository.save(credential);

        return GithubCredentialInfoRes.from(savedCredential);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GithubCredentialInfoRes> getGithubCredentials(Long workspaceId, Long memberId) {
        workspaceAccessValidator.requireAdmin(workspaceId, memberId);

        return githubCredentialRepository.findAllByWorkspaceId(workspaceId).stream()
                .map(GithubCredentialInfoRes::from)
                .toList();
    }

    @Override
    @Transactional
    public GithubCredentialInfoRes updateGithubCredential(Long workspaceId, Long credentialId, Long memberId, GithubCredentialUpdateReq req) {
        workspaceAccessValidator.requireAdmin(workspaceId, memberId);

        GithubCredential credential = githubCredentialRepository.findById(credentialId)
                .orElseThrow(() -> new ServiceException(CommonErrorCode.NOT_FOUND, "Credential not found", "해당 GitHub 자격 증명을 찾을 수 없습니다."));

        if (!credential.getWorkspace().getId().equals(workspaceId)) {
            throw new ServiceException(CommonErrorCode.FORBIDDEN, "Workspace mismatch", "해당 워크스페이스의 자격 증명이 아닙니다.");
        }

        // 부분 업데이트 수행
        credential.update(req.displayName(), req.token());

        return GithubCredentialInfoRes.from(credential);
    }

    @Override
    @Transactional
    public void deleteGithubCredential(Long workspaceId, Long credentialId, Long memberId) {
        workspaceAccessValidator.requireAdmin(workspaceId, memberId);

        GithubCredential credential = githubCredentialRepository.findById(credentialId)
                .orElseThrow(() -> new ServiceException(CommonErrorCode.NOT_FOUND, "Credential not found", "해당 GitHub 자격 증명을 찾을 수 없습니다."));

        if (!credential.getWorkspace().getId().equals(workspaceId)) {
            throw new ServiceException(CommonErrorCode.FORBIDDEN, "Workspace mismatch", "해당 워크스페이스의 자격 증명이 아닙니다.");
        }

        githubCredentialRepository.delete(credential);
    }
}