package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.knowledge.AgentHumorProfile;
import com.speaive.blog.domain.knowledge.HumorMaterial;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HumorMaterialRepository {
    List<HumorMaterial> findAll();
    Optional<HumorMaterial> findById(String id);
    void add(HumorMaterial material);
    /** 必须以 material.version()-1 做 CAS，不能覆盖其他编辑者的较新版本。 */
    void update(HumorMaterial material);
    Optional<AgentHumorProfile> findProfile(String agentId);
    /** 取最近 limit 次素材使用事件后去重；旧素材会随窗口推进重新获得推荐机会。 */
    List<String> recentlyUsedMaterialIds(String agentId, int limit);
    void recordUse(String runId, List<String> materialIds, Instant now);
}
