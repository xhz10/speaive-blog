package com.speaive.blog.infrastructure.knowledge.persistence.repository;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.out.persistence.HumorMaterialRepository;
import com.speaive.blog.domain.knowledge.AgentHumorProfile;
import com.speaive.blog.domain.knowledge.HumorMaterial;
import com.speaive.blog.infrastructure.knowledge.persistence.mapping.HumorPersistenceMapStructMapper;
import com.speaive.blog.infrastructure.knowledge.persistence.po.AgentHumorProfilePo;
import com.speaive.blog.infrastructure.knowledge.persistence.po.HumorMaterialPo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/** PostgreSQL is the source of truth; writes participate in the application transaction. */
public final class PostgresHumorMaterialRepository implements HumorMaterialRepository {
    private final JdbcTemplate jdbc;
    private final HumorPersistenceMapStructMapper mapping;

    public PostgresHumorMaterialRepository(JdbcTemplate jdbc, HumorPersistenceMapStructMapper mapping) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.mapping = Objects.requireNonNull(mapping, "mapping");
    }

    @Override
    public List<HumorMaterial> findAll() {
        return jdbc.query("SELECT * FROM blog_humor_material ORDER BY id", this::readMaterial)
                .stream().map(mapping::toDomain).toList();
    }

    @Override
    public Optional<HumorMaterial> findById(String id) {
        return jdbc.query("SELECT * FROM blog_humor_material WHERE id = ?", this::readMaterial, id)
                .stream().findFirst().map(mapping::toDomain);
    }

    @Override
    public void add(HumorMaterial material) {
        var row = mapping.toPo(material);
        int inserted = write(row, """
                INSERT INTO blog_humor_material (excerpt, source_url, source_title, source_kind, topics,
                    mechanism, analysis, adaptation, quality, enabled, version, updated_at, id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, false);
        if (inserted != 1) {
            throw new BlogException(BlogErrorCode.STORAGE_ERROR, "保存幽默素材失败");
        }
    }

    @Override
    public void update(HumorMaterial material) {
        var row = mapping.toPo(material);
        int changed = write(row, """
                UPDATE blog_humor_material SET excerpt = ?, source_url = ?, source_title = ?, source_kind = ?,
                    topics = ?, mechanism = ?, analysis = ?, adaptation = ?, quality = ?, enabled = ?,
                    version = ?, updated_at = ? WHERE id = ? AND version = ?
                """, true);
        if (changed != 1) {
            throw new BlogException(BlogErrorCode.VERSION_CONFLICT, "幽默素材已更新，请刷新后重试");
        }
    }

    @Override
    public Optional<AgentHumorProfile> findProfile(String agentId) {
        return jdbc.query("SELECT agent_id, mechanism, enabled FROM blog_agent_humor_profile WHERE agent_id = ?",
                (rows, index) -> new AgentHumorProfilePo(rows.getString("agent_id"),
                        rows.getString("mechanism"), rows.getBoolean("enabled")), agentId)
                .stream().findFirst().map(mapping::toDomain);
    }

    @Override
    public List<String> recentlyUsedMaterialIds(String agentId, int limit) {
        if (limit < 1) {
            return List.of();
        }
        return jdbc.queryForList("""
                SELECT usage.material_id
                FROM blog_agent_run_humor_material usage
                JOIN blog_agent_run run ON run.id = usage.run_id
                WHERE run.agent_id = ? AND run.status IN ('RUNNING', 'SUCCEEDED')
                ORDER BY usage.created_at DESC, run.started_at DESC, run.id DESC, usage.material_order
                LIMIT ?
                """, String.class, agentId, limit).stream().distinct().toList();
    }

    @Override
    public void recordUse(String runId, List<String> materialIds, Instant now) {
        Objects.requireNonNull(materialIds, "materialIds");
        if (materialIds.isEmpty()) {
            return;
        }
        List<String> distinct = List.copyOf(new LinkedHashSet<>(materialIds));
        jdbc.execute((ConnectionCallback<Integer>) connection -> {
            var array = connection.createArrayOf("text", distinct.toArray(String[]::new));
            try (var statement = connection.prepareStatement("""
                    INSERT INTO blog_agent_run_humor_material (run_id, material_id, material_order, created_at)
                    SELECT ?, item.material_id, (item.position - 1)::integer, ?
                    FROM UNNEST(?::text[]) WITH ORDINALITY AS item(material_id, position)
                    ON CONFLICT (run_id, material_id) DO NOTHING
                    """)) {
                statement.setString(1, runId);
                statement.setTimestamp(2, Timestamp.from(now));
                statement.setArray(3, array);
                return statement.executeUpdate();
            } finally {
                array.free();
            }
        });
    }

    private int write(HumorMaterialPo row, String sql, boolean updating) {
        return Objects.requireNonNull(jdbc.execute((ConnectionCallback<Integer>) connection -> {
            var topics = connection.createArrayOf("text", row.topics().toArray(String[]::new));
            try (var statement = connection.prepareStatement(sql)) {
                bindFields(statement, row);
                statement.setArray(5, topics);
                if (updating) {
                    statement.setLong(14, row.version() - 1);
                } else {
                    statement.setTimestamp(14, Timestamp.from(row.createdAt()));
                }
                return statement.executeUpdate();
            } finally {
                topics.free();
            }
        }));
    }

    private static void bindFields(PreparedStatement statement, HumorMaterialPo row) throws SQLException {
        statement.setString(1, row.excerpt());
        statement.setString(2, row.sourceUrl());
        statement.setString(3, row.sourceTitle());
        statement.setString(4, row.sourceKind());
        statement.setString(6, row.mechanism());
        statement.setString(7, row.analysis());
        statement.setString(8, row.adaptation());
        statement.setInt(9, row.quality());
        statement.setBoolean(10, row.enabled());
        statement.setLong(11, row.version());
        statement.setTimestamp(12, Timestamp.from(row.updatedAt()));
        statement.setString(13, row.id());
    }

    private HumorMaterialPo readMaterial(ResultSet rows, int index) throws SQLException {
        var topicsArray = rows.getArray("topics");
        List<String> topics;
        try {
            topics = List.copyOf(Arrays.asList((String[]) topicsArray.getArray()));
        } finally {
            topicsArray.free();
        }
        return new HumorMaterialPo(rows.getString("id"), rows.getString("excerpt"), rows.getString("source_url"),
                rows.getString("source_title"), rows.getString("source_kind"), topics,
                rows.getString("mechanism"), rows.getString("analysis"), rows.getString("adaptation"),
                rows.getInt("quality"), rows.getBoolean("enabled"), rows.getLong("version"),
                rows.getTimestamp("created_at").toInstant(), rows.getTimestamp("updated_at").toInstant());
    }
}
