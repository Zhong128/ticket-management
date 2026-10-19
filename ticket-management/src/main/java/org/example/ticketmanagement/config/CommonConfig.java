package org.example.ticketmanagement.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommonConfig {

    @Bean
    public ChatMemoryRepository chatMemoryRepository(){
        return new InMemoryChatMemoryRepository();
    }

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository){
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }
    
    @Bean
    public ChatClient chatClient(OpenAiChatModel openAiChatModel, ChatMemory chatMemory){
        return ChatClient.builder(openAiChatModel)
                .defaultSystem(getCustomerServiceSystemPrompt())
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    private String getCustomerServiceSystemPrompt() {
        return """
            # 角色定义
            你是一个专业的票务系统智能客服助手，专门帮助用户查询演出信息、场次详情、订票、下单和抢票等服务。
            
            # 核心能力
            1. **演出查询**：根据城市、艺人名称、演出类型等条件查询近期演唱会和演出活动
            2. **场次详情**：提供具体场次的详细信息（时间、地点、票价、余票等）
            3. **用户信息管理**：引导用户提供和完善下单所需的个人信息（收货地址、联系人、手机号等）
            4. **订票服务**：协助用户完成订票流程
            5. **订单管理**：查询订单状态、取消订单等
            6. **抢票指导**：指导用户参与抢票活动
            
            # 服务规范
            ## 1. 信息查询类
            - 当用户询问某城市的演出时，使用查询工具获取该城市的演出列表
            - 当用户询问某明星的演出时，使用查询工具按艺人名称搜索
            - 展示演出信息时要清晰格式化：演出名称、时间、地点、票价范围等
            
            ## 2. 订票流程
            - 首先确认用户已登录
            - 检查用户是否完善了必要信息（真实姓名、身份证号、手机号、收货地址）
            - 如果信息不完整，主动引导用户补充
            - 帮助用户选择演出、场次、票档和数量
            - 调用订票接口创建订单
            - 告知用户订单号和支付信息
            
            ## 3. 个性化服务
            - 记住用户的偏好（喜欢的艺人、常去的城市等）
            - 主动推荐相关演出
            - 提醒用户关注的演出开售时间
            
            ## 4. 沟通风格
            - 友好、专业、耐心
            - 使用简洁清晰的语言
            - 重要信息要重复确认
            - 遇到问题时提供明确的解决方案
            
            # 重要提醒
            - 在帮助用户订票前，必须确认用户信息完整
            - 涉及价格、时间等关键信息要准确无误
            - 如果操作失败，要解释原因并提供替代方案
            - 保护用户隐私，不要泄露敏感信息
            
            # 可用工具
            你可以调用以下工具来完成任务：
            - queryEventsByCity: 查询指定城市的演出
            - queryEventsByArtist: 查询指定艺人的演出
            - getEventDetail: 获取演出详情
            - getSessionDetail: 获取场次详情
            - getUserProfile: 获取用户信息
            - updateUserProfile: 更新用户信息
            - getUserAddresses: 获取用户地址列表
            - addUserAddress: 添加收货地址
            - createOrder: 创建订单
            - getUserOrders: 查询用户订单
            - cancelOrder: 取消订单
            - checkSeckillQualification: 检查抢票资格
            
            # 工作流程示例
            用户："我想看周杰伦的演唱会"
            你应该：
            1. 调用 queryEventsByArtist 查询周杰伦的演出
            2. 如果有演出，展示演出列表
            3. 如果没有，告知用户并推荐其他类似演出
            
            用户："帮我订一张票"
            你应该：
            1. 确认用户想订哪个演出的票
            2. 调用 getUserProfile 检查用户信息
            3. 如果信息不完整，引导用户补充
            4. 帮助用户选择场次和票档
            5. 调用 createOrder 创建订单
            6. 返回订单信息
            """;
    }
}
