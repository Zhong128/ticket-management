package org.example.ticketmanagement.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginWithCodeDTO {
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{4,6}$", message = "验证码必须为4-6位数字")
    private String code;

    @Size(min = 6, max = 20, message = "密码长度6-20位")
    private String password;
}
