package com.xniu.rental.asset.repository;

import com.xniu.rental.asset.model.AssetType;
import com.xniu.rental.asset.model.AssetTypeDefinition;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class AssetTypeRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<AssetTypeDefinition> mapper = new AssetTypeMapper();

    public AssetTypeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AssetTypeDefinition> list(boolean enabledOnly) {
        var sql = enabledOnly
            ? "SELECT * FROM asset_type_definition WHERE status = 'ENABLED' ORDER BY sort_order, id"
            : "SELECT * FROM asset_type_definition ORDER BY sort_order, id";
        return jdbcTemplate.query(sql, mapper);
    }

    public Optional<AssetTypeDefinition> findById(Long id) {
        return jdbcTemplate.query("SELECT * FROM asset_type_definition WHERE id = ?", mapper, id).stream().findFirst();
    }

    public Optional<AssetTypeDefinition> findByCodeOrName(String value) {
        return jdbcTemplate.query(
            "SELECT * FROM asset_type_definition WHERE UPPER(type_code) = UPPER(?) OR type_name = ? LIMIT 1",
            mapper,
            value,
            value
        ).stream().findFirst();
    }

    public Optional<AssetTypeDefinition> findSystemType(AssetType assetClass) {
        return jdbcTemplate.query(
            "SELECT * FROM asset_type_definition WHERE asset_class = ? AND system_defined = 1 LIMIT 1",
            mapper,
            assetClass.name()
        ).stream().findFirst();
    }

    public AssetTypeDefinition create(
        String typeCode,
        String typeName,
        AssetType assetClass,
        String serialLabel,
        Integer sortOrder,
        String status
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO asset_type_definition
                (type_code, type_name, asset_class, serial_label, system_defined, sort_order, status)
                VALUES (?, ?, ?, ?, 0, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, typeCode);
            statement.setString(2, typeName);
            statement.setString(3, assetClass.name());
            statement.setString(4, serialLabel);
            statement.setInt(5, sortOrder == null ? 0 : sortOrder);
            statement.setString(6, status);
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    public AssetTypeDefinition update(
        Long id,
        String typeName,
        AssetType assetClass,
        String serialLabel,
        Integer sortOrder,
        String status
    ) {
        jdbcTemplate.update("""
            UPDATE asset_type_definition
            SET type_name = ?, asset_class = ?, serial_label = ?, sort_order = ?, status = ?
            WHERE id = ?
            """, typeName, assetClass.name(), serialLabel, sortOrder == null ? 0 : sortOrder, status, id);
        return findById(id).orElseThrow();
    }

    public int countAssets(Long assetTypeId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM asset_item WHERE asset_type_id = ?",
            Integer.class,
            assetTypeId
        );
    }

    private static class AssetTypeMapper implements RowMapper<AssetTypeDefinition> {
        @Override
        public AssetTypeDefinition mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AssetTypeDefinition(
                rs.getLong("id"),
                rs.getString("type_code"),
                rs.getString("type_name"),
                AssetType.valueOf(rs.getString("asset_class")),
                rs.getString("serial_label"),
                rs.getBoolean("system_defined"),
                rs.getInt("sort_order"),
                rs.getString("status"),
                rs.getObject("created_at", java.time.LocalDateTime.class),
                rs.getObject("updated_at", java.time.LocalDateTime.class)
            );
        }
    }
}
