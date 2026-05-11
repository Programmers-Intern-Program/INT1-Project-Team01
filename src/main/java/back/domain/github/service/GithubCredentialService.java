package back.domain.github.service;

import back.domain.github.dto.request.GithubCredentialCreateReq;
import back.domain.github.dto.request.GithubCredentialUpdateReq;
import back.domain.github.dto.response.GithubCredentialInfoRes;

import java.util.List;

/**
 * GitHub Personal Access Token(PAT) 등 자격 증명 관리를 담당하는 서비스 인터페이스입니다.
 * <p>
 * Workspace 내에서 Agent가 GitHub Repository에 접근하여 코드를 작업하거나 PR을 생성할 때
 * 사용할 자격 증명의 등록, 조회, 삭제 등의 계약을 정의합니다.
 *
 * @author minhee
 * @since 2026-04-30
 */
public interface GithubCredentialService {

    /**
     * Workspace에 새로운 GitHub 자격 증명(PAT)을 등록합니다.
     * <p>
     * 동일한 Workspace 내에서 동일한 식별 이름(displayName)의 자격 증명이 중복 등록되지 않도록 검증합니다.
     * 전달된 평문 토큰은 JPA Converter에 의해 데이터베이스에 안전하게 암호화되어 저장됩니다.
     *
     * @param workspaceId 연동할 Workspace의 식별자
     * @param memberId    등록을 요청하는 사용자(ADMIN 권한 보유자)의 식별자
     * @param req         등록할 GitHub 자격 증명 정보 (Display Name, Token 원문)
     * @return 등록 완료된 자격 증명 정보 (민감한 토큰 원문은 마스킹 처리됨)
     */
    GithubCredentialInfoRes createGithubCredential(Long workspaceId, Long memberId, GithubCredentialCreateReq req);

    /**
     * Workspace에 등록된 모든 GitHub 자격 증명 정보를 조회합니다.
     *
     * @param workspaceId 조회할 Workspace 식별자
     * @param memberId    요청하는 사용자(MEMBER) 식별자
     * @return 자격 증명 정보 목록 (토큰 마스킹 처리됨)
     */
    List<GithubCredentialInfoRes> getGithubCredentials(Long workspaceId, Long memberId);

    /**
     * 등록된 GitHub 자격 증명 정보를 부분 수정(PATCH)합니다.
     * <p>
     * 전달된 DTO의 필드 중 null이거나 공백이 아닌 값만 기존 엔티티에 덮어씁니다.
     * 새로운 PAT가 전달되면 안전하게 암호화하여 덮어씁니다.
     *
     * @param workspaceId  Workspace 식별자
     * @param credentialId 수정할 자격 증명 식별자
     * @param memberId     요청하는 사용자(ADMIN) 식별자
     * @param req          수정할 필드를 담은 DTO
     * @return 수정된 자격 증명 정보
     */
    GithubCredentialInfoRes updateGithubCredential(Long workspaceId, Long credentialId, Long memberId, GithubCredentialUpdateReq req);

    /**
     * GitHub 자격 증명 정보를 삭제(Soft Delete)합니다.
     *
     * @param workspaceId  Workspace 식별자
     * @param credentialId 삭제할 자격 증명 식별자
     * @param memberId     요청하는 사용자(ADMIN) 식별자
     */
    void deleteGithubCredential(Long workspaceId, Long credentialId, Long memberId);
}