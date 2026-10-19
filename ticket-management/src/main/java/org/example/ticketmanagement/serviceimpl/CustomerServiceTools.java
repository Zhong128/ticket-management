package org.example.ticketmanagement.serviceimpl;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.dto.*;
import org.example.ticketmanagement.pojo.City;
import org.example.ticketmanagement.pojo.User;
import org.example.ticketmanagement.pojo.UserAddress;
import org.example.ticketmanagement.service.*;
import org.example.ticketmanagement.vo.EventSessionVO;
import org.example.ticketmanagement.vo.EventVO;
import org.example.ticketmanagement.vo.OrderInfoVO;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceTools {

    private final EventService eventService;
    private final EventSessionService sessionService;
    private final CityService cityService;
    private final UserService userService;
    private final UserAddressService addressService;
    private final OrderService orderService;
    private final SeckillService seckillService;

    /**
     * 根据城市名称查询演出列表
     */
    @Tool(description = "根据城市名称查询该城市的演出列表，返回演出基本信息")
    public List<EventVO> queryEventsByCity(
            @ToolParam(description = "城市名称，例如：北京、上海、广州") String cityName) {
        log.info("智能客服查询城市演出: {}", cityName);
        try {
            City city = cityService.getCityByName(cityName);
            if (city == null) {
                log.warn("城市不存在: {}", cityName);
                return List.of();
            }

            EventQueryDTO queryDTO = new EventQueryDTO();
            queryDTO.setCityId(city.getId());
            queryDTO.setStatus(1);
            queryDTO.setPage(1);
            queryDTO.setSize(10);

            return eventService.queryEvents(queryDTO).getRecords();
        } catch (Exception e) {
            log.error("查询城市演出失败", e);
            return List.of();
        }
    }

    /**
     * 根据艺人名称查询演出列表
     */
    @Tool(description = "根据艺人/明星名称查询其相关的演出列表")
    public List<EventVO> queryEventsByArtist(
            @ToolParam(description = "艺人或明星的姓名，例如：周杰伦、林俊杰") String artistName) {
        log.info("智能客服查询艺人演出: {}", artistName);
        try {
            EventQueryDTO queryDTO = new EventQueryDTO();
            queryDTO.setArtistName(artistName);
            queryDTO.setStatus(1);
            queryDTO.setPage(1);
            queryDTO.setSize(10);

            return eventService.queryEvents(queryDTO).getRecords();
        } catch (Exception e) {
            log.error("查询艺人演出失败", e);
            return List.of();
        }
    }

    /**
     * 获取演出详情
     */
    @Tool(description = "根据演出ID获取演出的详细信息，包括时间、地点、描述等")
    public EventVO getEventDetail(
            @ToolParam(description = "演出的ID") Long eventId) {
        log.info("智能客服查询演出详情: eventId={}", eventId);
        try {
            return eventService.getEventById(eventId);
        } catch (Exception e) {
            log.error("查询演出详情失败", e);
            return null;
        }
    }

    /**
     * 获取场次详情
     */
    @Tool(description = "根据场次ID获取场次的详细信息，包括时间、场馆、票价等")
    public EventSessionVO getSessionDetail(
            @ToolParam(description = "场次的ID") Long sessionId) {
        log.info("智能客服查询场次详情: sessionId={}", sessionId);
        try {
            return sessionService.getSessionById(sessionId);
        } catch (Exception e) {
            log.error("查询场次详情失败", e);
            return null;
        }
    }

    /**
     * 获取当前用户信息
     */
    @Tool(description = "获取当前登录用户的个人信息，用于检查用户信息是否完整")
    public UserProfileDTO getUserProfile() {
        log.info("智能客服获取用户信息");
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            User user = userService.getUserById(userId);
            if (user == null) {
                return null;
            }

            UserProfileDTO profileDTO = new UserProfileDTO();
            org.springframework.beans.BeanUtils.copyProperties(user, profileDTO);
            return profileDTO;
        } catch (Exception e) {
            log.error("获取用户信息失败", e);
            return null;
        }
    }

    /**
     * 更新用户信息
     */
    @Tool(description = "更新用户的个人信息，包括真实姓名、身份证号、手机号等")
    public Boolean updateUserProfile(
            @ToolParam(description = "真实姓名") String realName,
            @ToolParam(description = "身份证号码") String idCard,
            @ToolParam(description = "手机号码") String phone,
            @ToolParam(description = "昵称") String nickname) {
        log.info("智能客服更新用户信息");
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            User user = new User();
            user.setId(userId);
            user.setRealName(realName);
            user.setIdCard(idCard);
            user.setPhone(phone);
            user.setNickname(nickname);

            return userService.updateUser(user);
        } catch (Exception e) {
            log.error("更新用户信息失败", e);
            return false;
        }
    }

    /**
     * 获取用户地址列表
     */
    @Tool(description = "获取当前用户的所有收货地址列表")
    public List<UserAddress> getUserAddresses() {
        log.info("智能客服获取用户地址列表");
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            return addressService.listUserAddressesByUserIdEntity(userId);
        } catch (Exception e) {
            log.error("获取用户地址列表失败", e);
            return List.of();
        }
    }

    /**
     * 添加用户地址
     */
    @Tool(description = "为用户添加新的收货地址")
    public Boolean addUserAddress(
            @ToolParam(description = "收件人姓名") String receiverName,
            @ToolParam(description = "收件人手机号") String receiverPhone,
            @ToolParam(description = "省份") String province,
            @ToolParam(description = "城市") String city,
            @ToolParam(description = "区县") String district,
            @ToolParam(description = "详细地址") String detailAddress,
            @ToolParam(description = "邮政编码") String postalCode,
            @ToolParam(description = "是否设为默认地址，0-否，1-是，默认为0") Integer isDefault) {
        log.info("智能客服添加用户地址");
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            UserAddressDTO addressDTO = new UserAddressDTO();
            addressDTO.setUserId(userId);
            addressDTO.setReceiverName(receiverName);
            addressDTO.setReceiverPhone(receiverPhone);
            addressDTO.setProvince(province);
            addressDTO.setCity(city);
            addressDTO.setDistrict(district);
            addressDTO.setDetailAddress(detailAddress);
            addressDTO.setPostalCode(postalCode);
            addressDTO.setIsDefault(isDefault != null ? isDefault : 0);

            return addressService.addUserAddress(addressDTO);
        } catch (Exception e) {
            log.error("添加用户地址失败", e);
            return false;
        }
    }

    /**
     * 查询用户订单列表
     */
    @Tool(description = "查询当前用户的订单列表，返回最近的10个订单")
    public List<OrderInfoVO> getUserOrders() {
        log.info("智能客服查询用户订单列表");
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            OrderQueryDTO queryDTO = new OrderQueryDTO();
            queryDTO.setPage(1);
            queryDTO.setSize(10);

            return orderService.getUserOrders(queryDTO, userId).getRecords();
        } catch (Exception e) {
            log.error("查询用户订单失败", e);
            return List.of();
        }
    }

    /**
     * 检查抢票资格
     */
    @Tool(description = "检查用户是否有资格抢购某一场次的门票，包括库存和限购检查")
    public Boolean checkSeckillQualification(
            @ToolParam(description = "票档ID") Long tierId,
            @ToolParam(description = "购买数量") Integer quantity) {
        log.info("智能客服检查抢票资格: tierId={}, quantity={}", tierId, quantity);
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            return seckillService.checkSeckillQualification(userId, tierId, quantity);
        } catch (Exception e) {
            log.error("检查抢票资格失败", e);
            return false;
        }
    }
}
