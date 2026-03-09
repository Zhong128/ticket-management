package org.example.ticketmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CityDTO {
    @NotBlank(message = "城市名称不能为空")
    @Size(min = 2, max = 20, message = "城市名称长度必须在2-20个字符之间")
    private String name;

    @NotBlank(message = "城市代码不能为空")
    @Pattern(regexp = "^[A-Z_]+$", message = "城市代码必须由大写字母和下划线组成")
    @Size(max = 50, message = "城市代码不能超过50个字符")
    private String code;

    @NotBlank(message = "省份不能为空")
    @Size(min = 2, max = 20, message = "省份长度必须在2-20个字符之间")
    private String province;

    @NotNull(message = "热门等级不能为空")
    @Min(value = 0, message = "热门等级必须为0或1")
    @Max(value = 1, message = "热门等级必须为0或1")
    private Integer hotLevel;

    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态必须为0或1")
    @Max(value = 1, message = "状态必须为0或1")
    private Integer status;
}