// org/example/ticketmanagement/dto/LoginResponseDTO.java
package org.example.ticketmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponseDTO {
    private Long id;
    private String username;
    private String token;
    private String role;          // 新增：返回用户角色
    private Long expiresIn;       // 新增：token过期时间（秒）
    private Boolean isNewUser;    // 新增：是否新用户
}