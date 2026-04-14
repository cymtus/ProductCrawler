package com.mogoo.productcrawler.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class JacksonTypeHandler extends BaseTypeHandler<Map<String, Object>> {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, Object> parameter, JdbcType jdbcType) throws SQLException {
        try {
            ps.setString(i, mapper.writeValueAsString(parameter));
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, String col) throws SQLException {
        return parse(rs.getString(col));
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, int idx) throws SQLException {
        return parse(rs.getString(idx));
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement cs, int idx) throws SQLException {
        return parse(cs.getString(idx));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String json) {
        try {
            return json == null ? null : mapper.readValue(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }
}
