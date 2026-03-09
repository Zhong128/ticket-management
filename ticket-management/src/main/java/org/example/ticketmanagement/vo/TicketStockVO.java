// org/example/ticketmanagement/vo/TicketStockVO.java
package org.example.ticketmanagement.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketStockVO {
    private Long eventId;
    private Long sessionId;
    private boolean onSale;              // 是否已开票/开售
    private Boolean hasStock;            // 是否有库存（null-未开售，true-有库存，false-售罄）
    private String message;              // 提示文案
    private List<TierStockInfo> tierStockList; // 各票档库存详情

    /**
     * 票档库存信息内部类
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TierStockInfo {
        private Long id;
        private String tierName;
        private boolean hasStock;  // 该票档是否有库存
    }

    // 静态工厂方法 - 未开售
    public static TicketStockVO notOnSale(Long eventId, Long sessionId) {
        TicketStockVO vo = new TicketStockVO();
        vo.setEventId(eventId);
        vo.setSessionId(sessionId);
        vo.setOnSale(false);
        vo.setHasStock(null);  // 未开售时不显示库存状态
        vo.setMessage("演出尚未开售");
        return vo;
    }

    // 静态工厂方法 - 已开售且有库存
    public static TicketStockVO onSaleWithStock(Long eventId, Long sessionId, List<TierStockInfo> tierStockList) {
        TicketStockVO vo = new TicketStockVO();
        vo.setEventId(eventId);
        vo.setSessionId(sessionId);
        vo.setOnSale(true);
        vo.setHasStock(true);
        vo.setTierStockList(tierStockList);
        return vo;
    }

    // 静态工厂方法 - 已开售但售罄
    public static TicketStockVO onSaleSoldOut(Long eventId, Long sessionId) {
        TicketStockVO vo = new TicketStockVO();
        vo.setEventId(eventId);
        vo.setSessionId(sessionId);
        vo.setOnSale(true);
        vo.setHasStock(false);
        vo.setMessage("已售罄");
        return vo;
    }
}