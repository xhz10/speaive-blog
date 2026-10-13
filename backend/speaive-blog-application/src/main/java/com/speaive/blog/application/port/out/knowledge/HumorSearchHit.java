package com.speaive.blog.application.port.out.knowledge;

import com.speaive.blog.domain.knowledge.HumorMaterial;

public record HumorSearchHit(HumorMaterial material, double score) {}
