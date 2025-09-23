package com.matjom.matjom.auth.dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Builder
@Getter
public class SignUpRequest {
    @NotBlank   //빈 문자열, 공백 허용 안함
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 32, message = "비밀번호는 8~32자로 입력해야 합니다.")
    private String password;

    @NotBlank
    private String name;
}
