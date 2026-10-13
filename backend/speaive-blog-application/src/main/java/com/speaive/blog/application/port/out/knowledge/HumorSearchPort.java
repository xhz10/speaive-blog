package com.speaive.blog.application.port.out.knowledge;

import java.util.List;

/** 返回已启用素材的 BM25 候选；角色偏好、质量和使用历史由用例处理。 */
public interface HumorSearchPort {
    List<HumorSearchHit> search(HumorSearchQuery query);
}
