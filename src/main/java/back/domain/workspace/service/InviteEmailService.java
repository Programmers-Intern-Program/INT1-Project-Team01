package back.domain.workspace.service;

import back.domain.workspace.email.InviteEmailCommand;

/**
 * 워크스페이스 초대 이메일 발송을 처리합니다.
 */
public interface InviteEmailService {

    /**
     * 워크스페이스 초대 이메일 발송을 비동기로 요청합니다.
     *
     * @param command 초대 이메일 발송에 필요한 워크스페이스, 초대 링크, 수신자 정보
     */
    void sendAsync(InviteEmailCommand command);
}
