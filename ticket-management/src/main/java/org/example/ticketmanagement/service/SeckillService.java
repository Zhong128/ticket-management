// org/example/ticketmanagement/service/SeckillService.java
package org.example.ticketmanagement.service;

import org.example.ticketmanagement.dto.SeckillOrderDTO;
import org.example.ticketmanagement.vo.SeckillResultVO;

/**
 * 抢票服务接口
 */
public interface SeckillService {

    /**
     * 执行抢票操作
     * @param userId 用户ID（从登录信息获取）
     * @param seckillOrderDTO 抢票请求参数
     * @return 抢票结果
     */
    SeckillResultVO seckill(Long userId, SeckillOrderDTO seckillOrderDTO);

    /**
     * 检查抢票资格
     * @param userId 用户ID
     * @param tierId 票档ID
     * @param quantity 购买数量
     * @return 是否具有抢票资格
     */
    boolean checkSeckillQualification(Long userId, Long tierId, Integer quantity);

    /**
     * 预扣库存（Redis原子操作）
     * @param tierId 票档ID
     * @param quantity 购买数量
     * @return 是否扣减成功
     */
    boolean preDeductStock(Long tierId, Integer quantity);

    /**
     * 恢复库存（用于订单取消等情况）
     * @param tierId 票档ID
     * @param quantity 恢复数量
     */
    void restoreStock(Long tierId, Integer quantity);

    /**
     * 生成订单号（分布式唯一ID）
     * @return 订单号
     */
    String generateOrderNo();

    /**
     * 获取票档实时库存（从Redis）
     * @param tierId 票档ID
     * @return 实时库存数量
     */
    Integer getRealTimeStock(Long tierId);
}