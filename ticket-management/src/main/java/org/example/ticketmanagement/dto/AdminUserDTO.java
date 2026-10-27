package org.example.ticketmanagement.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/** 管理员编辑普通用户时只接收资料字段，敏感及未知字段直接拒绝。 */
public class AdminUserDTO extends UserProfileDTO {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("不允许修改字段：" + name);
    }
}
