package org.example.bidflow.domain.user.dto;

import lombok.Builder;
import lombok.Getter;
import org.example.bidflow.domain.user.entity.User;

@Getter
@Builder
public class UserSignUpResponse {

    private final String userUuid;

    public static UserSignUpResponse from(User user) {
        return UserSignUpResponse.builder()
                .userUuid(user.getUserUuid())
                .build();
    }
}
