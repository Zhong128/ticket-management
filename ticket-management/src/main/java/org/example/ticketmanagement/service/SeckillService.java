// org/example/ticketmanagement/service/SeckillService.java
package org.example.ticketmanagement.service;

import org.example.ticketmanagement.dto.SeckillOrderDTO;
import org.example.ticketmanagement.dto.SeckillQualificationDTO;
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

    /** 查询购票资格快照及具体原因，不锁库存或提交申请。 */
    SeckillQualificationDTO getSeckillQualification(Long userId, Long tierId, Integer quantity);

    /**
     * 获取票档实时库存（从Redis）
     * @param tierId 票档ID
     * @return 实时库存数量
     */
    Integer getRealTimeStock(Long tierId);
}
