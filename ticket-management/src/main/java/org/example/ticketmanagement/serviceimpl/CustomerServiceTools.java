package org.example.ticketmanagement.serviceimpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticketmanagement.dto.AgentQueryViews;
import org.example.ticketmanagement.dto.AgentToolResult;
import org.example.ticketmanagement.dto.EventQueryDTO;
import org.example.ticketmanagement.dto.OrderQueryDTO;
import org.example.ticketmanagement.dto.SeckillQualificationDTO;
import org.example.ticketmanagement.dto.TicketTierQueryDTO;
import org.example.ticketmanagement.pojo.City;
import org.example.ticketmanagement.pojo.PageResult;
import org.example.ticketmanagement.pojo.User;
import org.example.ticketmanagement.service.CityService;
import org.example.ticketmanagement.service.EventService;
import org.example.ticketmanagement.service.EventSessionService;
import org.example.ticketmanagement.service.OrderService;
import org.example.ticketmanagement.service.ReservationService;
import org.example.ticketmanagement.service.SeckillService;
import org.example.ticketmanagement.service.TicketTierService;
import org.example.ticketmanagement.service.UserAddressService;
import org.example.ticketmanagement.service.UserService;
import org.example.ticketmanagement.vo.EventSessionVO;
import org.example.ticketmanagement.vo.EventVO;
import org.example.ticketmanagement.vo.OrderInfoVO;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** 只读工具复用业务服务，身份由服务端上下文传入，不依赖请求线程。 */
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
    private final TicketTierService tierService;
    private final ReservationService reservationService;

    @Tool(description = "按城市名查询启用演出，第一页最多10条，不代表全部演出")
    public AgentToolResult<List<EventVO>> queryEventsByCity(@ToolParam(description = "城市名称") String cityName) {
        return query("queryEventsByCity", () -> {
            requireText(cityName);
            City city = cityService.getCityByName(cityName.trim());
            if (city == null) {
                return List.of();
            }
            EventQueryDTO dto = eventQuery();
            dto.setCityId(city.getId());
            return eventService.queryEvents(dto).getRecords();
        });
    }

    @Tool(description = "按艺人姓名查询启用演出，第一页最多10条")
    public AgentToolResult<List<EventVO>> queryEventsByArtist(@ToolParam(description = "艺人姓名") String artistName) {
        return query("queryEventsByArtist", () -> {
            requireText(artistName);
            EventQueryDTO dto = eventQuery();
            dto.setArtistName(artistName.trim());
            return eventService.queryEvents(dto).getRecords();
        });
    }

    @Tool(description = "按演出ID查询启用演出详情，场次和票档需另查")
    public AgentToolResult<EventVO> getEventDetail(@ToolParam(description = "演出ID") Long eventId) {
        return query("getEventDetail", () -> {
            requireId(eventId);
            return eventService.getPublicEventById(eventId);
        });
    }

    @Tool(description = "按演出ID查询启用场次，选择场次后可继续查询票档")
    public AgentToolResult<List<EventSessionVO>> getEventSessions(@ToolParam(description = "演出ID") Long eventId) {
        return query("getEventSessions", () -> {
            requireId(eventId);
            if (eventService.getPublicEventById(eventId) == null) {
                return List.of();
            }
            return sessionService.getSessionsByEventIdAndStatus(eventId, 1);
        });
    }

    @Tool(description = "查询启用场次的时间和场馆，不含票价与库存，需继续查询票档")
    public AgentToolResult<EventSessionVO> getSessionDetail(@ToolParam(description = "场次ID") Long sessionId) {
        return query("getSessionDetail", () -> {
            requireId(sessionId);
            return visibleSession(sessionId);
        });
    }

    @Tool(description = "按场次分页查询启用票档的名称和当前价格，库存需另查实时库存")
    public AgentToolResult<PageResult<AgentQueryViews.Tier>> getSessionTiers(
            @ToolParam(description = "场次ID") Long sessionId,
            @ToolParam(description = "页码，从1开始") Integer page,
            @ToolParam(description = "每页数量，1至20") Integer size) {
        return query("getSessionTiers", () -> {
            requireId(sessionId);
            requirePage(page, size);
            if (visibleSession(sessionId) == null) {
                return null;
            }
            TicketTierQueryDTO dto = new TicketTierQueryDTO();
            dto.setSessionId(sessionId);
            dto.setStatus(1);
            dto.setPage(page);
            dto.setSize(size);
            var result = tierService.queryTicketTiers(dto);
            var records = result.getRecords().stream().map(tier -> new AgentQueryViews.Tier(
                    textId(tier.getId()), textId(tier.getEventId()), textId(tier.getSessionId()),
                    tier.getTierName(), tier.getCurrentPrice())).toList();
            return new PageResult<>(records, result.getTotal(), result.getPage(), result.getSize());
        });
    }

    @Tool(description = "查询启用票档的实时库存快照，不预留库存、不保证购票成功")
    public AgentToolResult<Integer> getRealTimeStock(@ToolParam(description = "票档ID") Long tierId) {
        return query("getRealTimeStock", () -> {
            requireId(tierId);
            var tier = tierService.getTicketTierById(tierId);
            if (tier == null || !Integer.valueOf(1).equals(tier.getStatus())) {
                return null;
            }
            if (eventService.getPublicEventById(tier.getEventId()) == null
                    || visibleSession(tier.getSessionId()) == null) {
                return null;
            }
            return seckillService.getRealTimeStock(tierId);
        });
    }

    @Tool(description = "查询本人资料完整性和脱敏手机号，不返回姓名、邮箱、证件号或密码")
    public AgentToolResult<AgentQueryViews.Profile> getUserProfile(ToolContext toolContext) {
        return query("getUserProfile", () -> {
            User user = userService.getUserById(userId(toolContext));
            return user == null ? null : new AgentQueryViews.Profile(hasText(user.getRealName()),
                    hasText(user.getPhone()), hasText(user.getIdCard()), maskPhone(user.getPhone()));
        });
    }

    @Tool(description = "查询本人地址的省市区、脱敏手机号和默认标记，不返回姓名及详细门牌")
    public AgentToolResult<List<AgentQueryViews.Address>> getUserAddresses(ToolContext toolContext) {
        return query("getUserAddresses", () -> addressService.listUserAddressesByUserIdEntity(userId(toolContext))
                .stream().map(address -> new AgentQueryViews.Address(textId(address.getId()),
                        address.getProvince(), address.getCity(), address.getDistrict(),
                        maskPhone(address.getReceiverPhone()), Integer.valueOf(1).equals(address.getIsDefault())))
                .toList());
    }

    @Tool(description = "分页查询本人订单，可选订单状态筛选")
    public AgentToolResult<PageResult<AgentQueryViews.Order>> getUserOrders(
            @ToolParam(description = "页码，从1开始") Integer page,
            @ToolParam(description = "每页数量，1至20") Integer size,
            @ToolParam(description = "可选状态：0待支付、1已支付、2已取消、3已完成、4取消中", required = false) Integer status,
            ToolContext toolContext) {
        return query("getUserOrders", () -> {
            long currentUser = userId(toolContext);
            requirePage(page, size);
            if (status != null && (status < 0 || status > 4)) {
                throw new IllegalArgumentException("订单状态无效");
            }
            OrderQueryDTO dto = new OrderQueryDTO();
            dto.setPage(page);
            dto.setSize(size);
            dto.setOrderStatus(status);
            var result = orderService.getUserOrders(dto, currentUser);
            return new PageResult<>(result.getRecords().stream().map(this::orderView).toList(),
                    result.getTotal(), result.getPage(), result.getSize());
        });
    }

    @Tool(description = "查询本人订单详情，业务服务校验订单归属")
    public AgentToolResult<AgentQueryViews.Order> getOrderDetail(
            @ToolParam(description = "订单ID") Long orderId, ToolContext toolContext) {
        return query("getOrderDetail", () -> {
            requireId(orderId);
            var order = orderService.getOrderDetail(orderId, userId(toolContext));
            return order == null ? null : orderView(order);
        });
    }

    @Tool(description = "查询本人购票申请，已受理不代表已建单，已建单不代表已付款")
    public AgentToolResult<Map<String, Object>> getReservationStatus(
            @ToolParam(description = "购票申请ID，不是订单ID") Long requestId, ToolContext toolContext) {
        return query("getReservationStatus", () -> {
            requireId(requestId);
            var source = reservationService.request(requestId, userId(toolContext));
            // 申请业务视图可能继续扩展，只向模型提供回答状态所需的字段。
            Map<String, Object> view = new LinkedHashMap<>();
            for (String field : List.of("requestId", "requestStatus", "orderId", "orderNo", "expireTime",
                    "tierId", "quantity", "price", "totalAmount")) {
                if (source.containsKey(field)) {
                    Object value = source.get(field);
                    view.put(field, field.endsWith("Id") && value != null ? value.toString() : value);
                }
            }
            return view;
        });
    }

    @Tool(description = "检查本人购票资格并返回具体原因，仅查询，不锁库存、不提交申请")
    public AgentToolResult<SeckillQualificationDTO> checkSeckillQualification(
            @ToolParam(description = "票档ID") Long tierId,
            @ToolParam(description = "购买数量，1至5") Integer quantity, ToolContext toolContext) {
        var result = query("checkSeckillQualification", () -> {
            requireId(tierId);
            return seckillService.getSeckillQualification(userId(toolContext), tierId, quantity);
        });
        if (result.data() != null && !result.data().qualified()) {
            return new AgentToolResult<>("REJECTED", result.data(), result.data().reason(), result.data().message());
        }
        return result;
    }

    private <T> AgentToolResult<T> query(String name, Supplier<T> operation) {
        try {
            T data = operation.get();
            if (data == null || data instanceof Collection<?> values && values.isEmpty()
                    || data instanceof PageResult<?> page && page.isEmpty()) {
                return AgentToolResult.empty(data);
            }
            return AgentToolResult.success(data);
        } catch (IllegalArgumentException invalid) {
            return AgentToolResult.rejected("INVALID_OR_INACCESSIBLE", "参数无效或记录不可访问，请核对后重试");
        } catch (Exception failure) {
            // 不记录第三方异常正文，避免其中包含个人信息或凭据。
            log.warn("客服工具查询失败: tool={}, exception={}", name, failure.getClass().getSimpleName());
            return AgentToolResult.error();
        }
    }

    private long userId(ToolContext context) {
        Object identity = context == null ? null : context.getContext().get("userId");
        if (!(identity instanceof Long id) || id <= 0) {
            throw new IllegalArgumentException("缺少服务端身份");
        }
        return id;
    }

    private EventQueryDTO eventQuery() {
        EventQueryDTO dto = new EventQueryDTO();
        dto.setStatus(1);
        dto.setPage(1);
        dto.setSize(10);
        return dto;
    }

    private AgentQueryViews.Order orderView(OrderInfoVO order) {
        return new AgentQueryViews.Order(textId(order.getId()), order.getOrderNo(), order.getEventName(),
                order.getSessionName(), order.getSessionTime(), order.getTierName(), order.getQuantity(),
                order.getTotalAmount(), order.getOrderStatus(), order.getOrderStatusDesc(),
                order.getExpireTime(), order.getPayTime());
    }

    private EventSessionVO visibleSession(Long sessionId) {
        EventSessionVO session = sessionService.getSessionById(sessionId);
        if (session == null || !Integer.valueOf(1).equals(session.getStatus())
                || eventService.getPublicEventById(session.getEventId()) == null) {
            return null;
        }
        return session;
    }

    private void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID无效");
        }
    }

    private void requirePage(Integer page, Integer size) {
        if (page == null || page < 1 || page > 1000 || size == null || size < 1 || size > 20) {
            throw new IllegalArgumentException("分页参数无效");
        }
    }

    private void requireText(String value) {
        if (!hasText(value) || value.length() > 100) {
            throw new IllegalArgumentException("查询词不能为空且最多100字");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String maskPhone(String phone) {
        return phone != null && phone.matches("\\d{11}")
                ? phone.substring(0, 3) + "****" + phone.substring(7) : null;
    }

    private String textId(Long id) {
        return id == null ? null : id.toString();
    }
}
