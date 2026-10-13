package com.speaive.blog.application.service;

import com.speaive.blog.application.command.knowledge.SaveHumorMaterialCommand;
import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.in.knowledge.HumorKnowledgeUseCase;
import com.speaive.blog.application.port.out.knowledge.HumorSearchPort;
import com.speaive.blog.application.port.out.knowledge.HumorSearchQuery;
import com.speaive.blog.application.port.out.persistence.HumorMaterialRepository;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.application.result.knowledge.HumorMaterialResult;
import com.speaive.blog.domain.error.DomainException;
import com.speaive.blog.domain.knowledge.HumorMaterial;
import com.speaive.blog.domain.knowledge.HumorMechanism;
import com.speaive.blog.domain.knowledge.HumorSourceKind;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** 素材维护与检索预览；评论取材由评论用例按角色绑定单独编排。 */
public final class HumorKnowledgeApplicationService implements HumorKnowledgeUseCase {
    private final HumorMaterialRepository materials;
    private final HumorSearchPort search;
    private final TransactionRunner transactions;
    private final Clock clock;

    public HumorKnowledgeApplicationService(HumorMaterialRepository materials, HumorSearchPort search,
            TransactionRunner transactions, Clock clock) {
        this.materials = java.util.Objects.requireNonNull(materials);
        this.search = java.util.Objects.requireNonNull(search);
        this.transactions = java.util.Objects.requireNonNull(transactions);
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    @Override public List<HumorMaterialResult> listMaterials() {
        return transactions.required(() -> materials.findAll().stream().map(value -> result(value, 0)).toList());
    }

    @Override public List<HumorMaterialResult> searchMaterials(String text, String mechanism) {
        return validated(() -> search.search(new HumorSearchQuery(text,
                mechanism == null || mechanism.isBlank() ? null : enumValue(HumorMechanism.class, mechanism), 20))
                .stream().map(hit -> result(hit.material(), hit.score())).toList());
    }

    @Override public HumorMaterialResult createMaterial(SaveHumorMaterialCommand command) {
        return validated(() -> transactions.required(() -> {
            HumorMaterial material = HumorMaterial.create(UUID.randomUUID().toString(), command.excerpt(),
                    command.sourceUrl(), command.sourceTitle(), enumValue(HumorSourceKind.class, command.sourceKind()),
                    command.topics(), enumValue(HumorMechanism.class, command.mechanism()), command.analysis(),
                    command.adaptation(), command.quality(), command.enabled(), clock.instant());
            materials.add(material);
            return result(material, 0);
        }));
    }

    @Override public HumorMaterialResult updateMaterial(String id, SaveHumorMaterialCommand command) {
        return validated(() -> transactions.required(() -> {
            HumorMaterial current = materials.findById(id).orElseThrow(() ->
                    new BlogException(BlogErrorCode.NOT_FOUND, "素材不存在"));
            if (current.version() != command.version()) {
                throw new BlogException(BlogErrorCode.VERSION_CONFLICT, "素材已更新，请刷新后重试");
            }
            HumorMaterial updated = current.revise(command.excerpt(), command.sourceUrl(), command.sourceTitle(),
                    enumValue(HumorSourceKind.class, command.sourceKind()), command.topics(),
                    enumValue(HumorMechanism.class, command.mechanism()), command.analysis(), command.adaptation(),
                    command.quality(), command.enabled(), clock.instant());
            materials.update(updated);
            return result(updated, 0);
        }));
    }

    private static HumorMaterialResult result(HumorMaterial material, double score) {
        return new HumorMaterialResult(material.id(), material.excerpt(), material.sourceUrl(), material.sourceTitle(),
                material.sourceKind().name(), material.topics(), material.mechanism().name(), material.analysis(),
                material.adaptation(), material.quality(), material.enabled(), material.version(),
                material.createdAt(), material.updatedAt(), score);
    }

    private static <T> T validated(Supplier<T> action) {
        try { return action.get(); }
        catch (DomainException | IllegalArgumentException e) {
            throw new BlogException(BlogErrorCode.INVALID_REQUEST, e.getMessage());
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("素材分类不能为空");
        try { return Enum.valueOf(type, value.trim()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("素材分类无效，请选择列表中的选项"); }
    }
}
