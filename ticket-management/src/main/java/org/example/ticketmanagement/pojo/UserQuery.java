package org.example.ticketmanagement.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
//比较当前类 + 父类字段
@EqualsAndHashCode(callSuper = true)
@Data
public class UserQuery extends PageQuery {
    private String username;   // 用户名模糊查询
    private String email;      // 邮箱模糊查询
    private String phone;      // 手机号精确查询
    private String nickname;   // 昵称模糊查询
    private String realName;   // 真实姓名模糊查询
    private Integer status;    // 状态：0-禁用，1-启用
    private String role;       // 角色：USER, ADMIN
    private LocalDate birthdayFrom;  // 生日范围查询开始
    private LocalDate birthdayTo;    // 生日范围查询结束
    private LocalDateTime createTimeFrom;  // 创建时间范围开始
    private LocalDateTime createTimeTo;    // 创建时间范围结束

}