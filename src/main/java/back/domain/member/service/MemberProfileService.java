package back.domain.member.service;

import back.domain.member.dto.request.MemberProfileUpdateReq;
import back.domain.member.dto.response.MemberProfileRes;

public interface MemberProfileService {
    MemberProfileRes getMyProfile(long memberId);

    MemberProfileRes updateMyProfile(long memberId, MemberProfileUpdateReq request);
}
