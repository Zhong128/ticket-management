package org.example.ticketmanagement.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.example.ticketmanagement.dto.AgentCitation;
import org.example.ticketmanagement.exception.AgentRequestException;
import org.example.ticketmanagement.mapper.AgentKnowledgeMapper;
import org.example.ticketmanagement.pojo.AgentKnowledgeEntry;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AgentKnowledgeService {

    private static final Pattern WORDS = Pattern.compile("[\\p{IsHan}]+|[a-z0-9_]+");
    private static final Set<String> STOP_WORDS = Set.of("什么", "怎么", "为什么", "可以", "一下", "我的",
            "这个", "当前", "现在", "请问", "一个", "是不是", "是否", "一下子", "怎么办",
            "帮我", "我想", "想要", "能否", "能不", "不能", "可不", "不可以", "如何", "等于", "具体");
    private static final List<String> BUSINESS_TERMS = List.of("退款", "退票", "取消", "支付", "付款", "订单",
            "申请", "库存", "票档", "资格", "地址", "密码", "登录", "注册", "客服", "会话", "选座",
            "到账", "手续费", "演出", "场次", "改签", "转赠", "购票", "抢票", "门票", "张票", "卡片",
            "草稿", "收银台", "账号", "账户", "实名", "证件", "验证码", "票价", "支付宝", "微信", "银行卡",
            "限购", "清空", "排期", "身份证", "收货", "付款期限");
    private static final List<String> EXTERNAL_DOMAINS = List.of("酒店", "民宿", "客栈", "订房", "机票", "航班",
            "火车", "高铁", "12306", "外卖", "快递", "网购", "电商", "衣服", "股票", "证券", "基金",
            "保险", "贷款", "淘宝", "京东", "拼多多", "携程", "飞猪", "美团", "游戏充值");
    private static final List<String> PERFORMANCE_CONTEXT = List.of("演出", "音乐会", "演唱会", "音乐节",
            "话剧", "戏剧", "场次", "票档", "演出票");
    private static final List<BusinessExpression> EXPRESSIONS = List.of(
            new BusinessExpression("选座", List.of("座位", "挑座", "指定座")),
            new BusinessExpression("改签", List.of("换日期", "换时间", "改日期", "换场次")),
            new BusinessExpression("转赠", List.of("送给别人", "送给朋友", "送人", "转给朋友", "转让")),
            new BusinessExpression("库存", List.of("余票", "余量", "剩余名额", "卖光", "卖完")),
            new BusinessExpression("付款", List.of("付钱", "结账", "结帐")),
            new BusinessExpression("支付", List.of("扣钱", "扣款")),
            new BusinessExpression("退款", List.of("退钱", "返款")),
            new BusinessExpression("会话", List.of("聊天", "对话")),
            new BusinessExpression("清空", List.of("删掉", "删除聊天", "清除记录", "删除记录")),
            new BusinessExpression("登录", List.of("登陆")),
            new BusinessExpression("购票", List.of("买票", "订票", "点购买", "点击购买")),
            new BusinessExpression("工具上限", List.of("次数用完", "调用次数", "调用上限")),
            new BusinessExpression("票价", List.of("确认价", "涨价")),
            new BusinessExpression("场次", List.of("日期选项", "演出日期")),
            new BusinessExpression("票档", List.of("价位", "价格档")),
            new BusinessExpression("取消", List.of("撤销订单", "撤单")));
    private final AgentKnowledgeMapper mapper;

    public List<AgentKnowledgeEntry> search(String question) {
        if (question == null || question.isBlank() || question.length() > 2000) {
            throw new AgentRequestException(400, "规则查询内容无效");
        }
        String normalized = Normalizer.normalize(question, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        // 只处理本站业务方向；外部领域的比较问题有明确演出目标时仍允许查本站规则。
        if (containsAny(normalized, EXTERNAL_DOMAINS) && !containsAny(normalized, PERFORMANCE_CONTEXT)) {
            return List.of();
        }
        boolean businessIntent = containsAny(normalized, BUSINESS_TERMS) || containsAny(normalized, PERFORMANCE_CONTEXT)
                || EXPRESSIONS.stream()
                .anyMatch(expression -> normalized.contains(expression.canonical())
                        || containsAny(normalized, expression.variants()));
        if (!businessIntent) {
            return List.of();
        }

        List<String> terms = terms(normalized);
        if (terms.isEmpty()) {
            return List.of();
        }
        return mapper.search(terms, 5);
    }

    public AgentKnowledgeEntry get(String entryId, Integer version) {
        if (entryId == null || !entryId.matches("KB-[A-Z]+-\\d{3}")
                || version != null && (version < 1 || version > 1_000_000)) {
            throw new AgentRequestException(400, "规则编号或版本无效");
        }
        AgentKnowledgeEntry entry = mapper.entry(entryId, version);
        if (entry == null) {
            throw new AgentRequestException(404, "该规则不存在、已停用或该版本不可用");
        }
        return entry;
    }

    public List<AgentCitation> citations(List<AgentKnowledgeEntry> entries) {
        return entries.stream().map(entry -> {
            if (!entry.getEntryId().matches("KB-[A-Z]+-\\d{3}") || entry.getVersion() < 1) {
                throw new IllegalStateException("知识来源标识无效");
            }
            return new AgentCitation(entry.getEntryId(), entry.getDocumentId(), entry.getQuestion(),
                    entry.getVersion(), entry.getReviewedAt().toString(),
                    "/support/knowledge/" + entry.getEntryId() + "?version=" + entry.getVersion());
        }).toList();
    }

    private List<String> terms(String normalized) {
        LinkedHashSet<String> words = new LinkedHashSet<>();
        // 同义业务词先进入固定预算，避免长问题的通用双字片段挤掉关键意图。
        for (BusinessExpression expression : EXPRESSIONS) {
            if (containsAny(normalized, expression.variants())) {
                words.add(expression.canonical());
            }
        }
        if (normalized.contains("截止") && (normalized.contains("过了") || normalized.contains("超过"))) {
            words.add("过期");
        }
        // 先保留常见业务词，避免长问题的开头寒暄占满检索预算。
        for (String keyword : BUSINESS_TERMS) {
            if (words.size() < 24 && normalized.contains(keyword)) {
                words.add(keyword);
            }
        }
        var matcher = WORDS.matcher(normalized);
        while (matcher.find() && words.size() < 24) {
            String word = matcher.group();
            if (!Character.UnicodeScript.HAN.equals(Character.UnicodeScript.of(word.codePointAt(0)))) {
                if (word.length() >= 2 && word.length() <= 64) {
                    words.add(word);
                }
                continue;
            }
            // 中文双字片段不依赖新分词库；固定去重和上限，SQL只使用LOCATE绑定词项。
            int[] points = word.codePoints().toArray();
            for (int i = 0; i + 1 < points.length && words.size() < 24; i++) {
                String term = new String(points, i, 2);
                if (!STOP_WORDS.contains(term)) {
                    words.add(term);
                }
            }
        }
        return new ArrayList<>(words);
    }

    private boolean containsAny(String text, List<String> expressions) {
        return expressions.stream().anyMatch(text::contains);
    }

    private record BusinessExpression(String canonical, List<String> variants) {
    }
}
