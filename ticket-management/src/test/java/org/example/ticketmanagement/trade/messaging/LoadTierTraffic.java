package org.example.ticketmanagement.trade.messaging;

/** 隔离压测流量分配：80%热点、两个普通票档各10%，且各票档覆盖两个节点。 */
final class LoadTierTraffic {
    static int targetTier(int firstTier, int buyerIndex) {
        // 每10个买家的前8个进入热点；剩余两个按批次轮换普通票档，避免与节点奇偶分流绑定。
        return buyerIndex % 10 < 8 ? firstTier : firstTier + 1 + (buyerIndex / 10) % 2;
    }
}
