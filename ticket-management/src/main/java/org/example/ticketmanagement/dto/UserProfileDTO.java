package org.example.ticketmanagement.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

import java.time.LocalDate;
/**
 * 用户信息DTO类，专门用于修改个人信息等方法
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileDTO {

    @Size(min = 3, max = 20, message = "用户名长度必须在3-20个字符之间")
    private String username;

    @Email(message = "邮箱格式不正确")
    private String email;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Size(max = 20, message = "昵称长度不能超过20个字符")
    private String nickname;

    private String avatar;

    @Size(max = 10, message = "真实姓名长度不能超过10个字符")
    private String realName;

    @Min(value = 0, message = "性别值不合法")
    @Max(value = 2, message = "性别值不合法")
    private Integer gender;

    private LocalDate birthday;
}
