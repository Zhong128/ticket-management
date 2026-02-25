package org.example.ticketmanagement.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserAddressDTO {
    @JsonIgnore  // 忽略该字段，已经强制设定
    private Long id;
    
    @JsonIgnore
    private Long userId;

    @NotBlank(message = "收件人姓名不能为空")
    @Size(max = 10, message = "真实姓名长度不能超过10个字符")
    private String receiverName;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String receiverPhone;

    @NotBlank(message = "省份不能为空")
    private String province;

    @NotBlank(message = "城市不能为空")
    private String city;

    private String district;

    @NotBlank(message = "详细地址不能为空")
    private String detailAddress;

    @Pattern(regexp = "^\\d{6}$", message = "邮政编码格式不正确，应为6位数字")
    private String postalCode;
    @JsonIgnore
    private Integer isDefault;
}