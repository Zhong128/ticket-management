package org.example.ticketmanagement.mapper.typehandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.example.ticketmanagement.dto.AgentCitation;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** 仅由客服Mapper显式引用，不注册为项目所有List字段的默认处理器。 */
public class AgentCitationTypeHandler extends BaseTypeHandler<List<AgentCitation>> {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<AgentCitation>> TYPE = new TypeReference<>() {};

    public static String encode(List<AgentCitation> citations) {
        try {
            if (citations == null || citations.size() > 5) {
                throw new IllegalArgumentException("来源数量无效");
            }
            return JSON.writeValueAsString(citations);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("来源保存格式无效");
        }
    }

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, List<AgentCitation> citations,
                                    JdbcType jdbcType) throws SQLException {
        statement.setString(index, encode(citations));
    }

    @Override
    public List<AgentCitation> getNullableResult(ResultSet result, String column) throws SQLException {
        return decode(result.getString(column));
    }

    @Override
    public List<AgentCitation> getNullableResult(ResultSet result, int index) throws SQLException {
        return decode(result.getString(index));
    }

    @Override
    public List<AgentCitation> getNullableResult(CallableStatement statement, int index) throws SQLException {
        return decode(statement.getString(index));
    }

    private List<AgentCitation> decode(String data) throws SQLException {
        if (data == null || data.isBlank()) {
            return List.of();
        }
        try {
            List<AgentCitation> citations = JSON.readValue(data, TYPE);
            if (citations == null || citations.size() > 5) {
                throw new SQLException("客服来源数据格式无效");
            }
            return List.copyOf(citations);
        } catch (JsonProcessingException failure) {
            throw new SQLException("客服来源数据格式无效");
        }
    }
}
