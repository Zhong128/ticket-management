package org.example.ticketmanagement.dto;

/** 资格检查仅为当前快照，不预留库存，最终结果以购票申请为准。 */
public record SeckillQualificationDTO(boolean qualified, String reason, String message) {

    public static SeckillQualificationDTO allowed() {
        return new SeckillQualificationDTO(true, "ELIGIBLE", "当前满足购票条件，不代表库存已锁定或购票成功");
    }

    public static SeckillQualificationDTO denied(String reason, String message) {
        return new SeckillQualificationDTO(false, reason, message);
    }
}
