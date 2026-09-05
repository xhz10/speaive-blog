package com.speaive.blog.application.service;

import com.speaive.blog.application.command.comment.GenerateCommentBatchCommand;
import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.in.comment.CommentBatchUseCase;
import com.speaive.blog.application.port.in.comment.CommentUseCase;
import com.speaive.blog.application.port.out.ai.AiGenerationTaskRunner;
import com.speaive.blog.application.port.out.persistence.AgentRepository;
import com.speaive.blog.application.port.out.persistence.PostQueryScope;
import com.speaive.blog.application.port.out.persistence.PostRepository;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.application.result.comment.CommentBatchResult;
import com.speaive.blog.application.result.comment.CommentBatchResult.Item;
import com.speaive.blog.domain.error.DomainException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.Supplier;

/**
 * 批量生成的独立编排器，复用单角色用例的审核、审计及事务规则。
 * 先检查角色权限，再预热一次文章摘要，最后通过出站端口并行调用模型。
 */
public final class CommentBatchApplicationService implements CommentBatchUseCase {
    private final CommentUseCase comments;
    private final PostRepository posts;
    private final AgentRepository agents;
    private final TransactionRunner transactions;
    private final AiGenerationTaskRunner runner;

    public CommentBatchApplicationService(CommentUseCase comments, PostRepository posts,
            AgentRepository agents, TransactionRunner transactions, AiGenerationTaskRunner runner) {
        this.comments = comments;
        this.posts = posts;
        this.agents = agents;
        this.transactions = transactions;
        this.runner = runner;
    }

    @Override
    public CommentBatchResult generate(String postSlug, GenerateCommentBatchCommand command) {
        if (command == null || command.agentIds() == null || command.agentIds().isEmpty()
                || command.agentIds().size() > 20 || command.agentIds().stream()
                .anyMatch(id -> id == null || id.isBlank() || id.length() > 36)
                || new HashSet<>(command.agentIds()).size() != command.agentIds().size()) {
            throw new BlogException(BlogErrorCode.INVALID_REQUEST, "请选择 1 至 20 个不同的角色");
        }
        List<String> ids = List.copyOf(command.agentIds());
        var failures = new HashMap<String, Item>();
        // 短事务只读快照；单角色用例在真正生成前仍会重新检查权限及状态。
        String postId = transactions.required(() -> {
            var post = posts.findBySlug(postSlug, PostQueryScope.STUDIO)
                    .orElseThrow(() -> new BlogException(BlogErrorCode.NOT_FOUND, "文章不存在"));
            for (String id : ids) {
                try {
                    var agent = agents.findById(id).orElseThrow(() ->
                            new BlogException(BlogErrorCode.NOT_FOUND, "角色不存在"));
                    agent.ensureCanGenerate(post.visibility());
                } catch (BlogException e) {
                    failures.put(id, failure(id, e.code().name(), e.getMessage()));
                } catch (DomainException e) {
                    failures.put(id, failure(id, "INVALID_REQUEST", e.getMessage()));
                }
            }
            return post.id();
        });
        List<Supplier<Item>> tasks = new ArrayList<>();
        for (String id : ids) {
            if (!failures.containsKey(id)) tasks.add(() -> generateOne(postSlug, id));
        }
        if (!tasks.isEmpty()) {
            // 无合规角色时不发送正文；同批角色共享当前修订的摘要，避免重复付费生成。
            for (Item item : runner.run(postId, () -> comments.generateAiSummary(postSlug), tasks)) {
                failures.put(item.agentId(), item);
            }
        }
        return new CommentBatchResult(ids.stream().map(failures::get).toList());
    }

    private Item generateOne(String slug, String id) {
        try {
            return new Item(id, comments.generateAiComment(slug, id), null, null);
        } catch (BlogException e) {
            return failure(id, e.code().name(), e.getMessage());
        } catch (RuntimeException e) {
            // 不能把数据库或供应商的内部异常文本暴露到浏览器。
            return failure(id, "AI_GENERATION_FAILED", "角色生成失败，请稍后重试");
        }
    }

    private static Item failure(String id, String code, String message) {
        return new Item(id, null, code, message);
    }
}
