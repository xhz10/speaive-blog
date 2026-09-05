package com.speaive.blog;

import com.speaive.blog.application.service.VisitAnalyticsApplicationService;
import com.speaive.blog.application.port.out.analytics.VisitorContextPort;
import com.speaive.blog.application.port.out.persistence.ArticleVisitRepository;
import com.speaive.blog.infrastructure.analytics.LocalVisitorContextAdapter;
import com.speaive.blog.infrastructure.content.persistence.mapping.ArticleVisitPersistenceMapper;
import com.speaive.blog.infrastructure.content.persistence.repository.PostgresArticleVisitRepository;
import com.speaive.blog.interfaces.http.analytics.VisitRequestGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import com.speaive.blog.application.port.in.importing.MarkdownInboxUseCase;
import com.speaive.blog.application.port.in.comment.CommentUseCase;
import com.speaive.blog.application.port.out.ai.AiGenerationTaskRunner;
import com.speaive.blog.application.service.CommentBatchApplicationService;
import com.speaive.blog.infrastructure.ai.VirtualThreadAiGenerationTaskRunner;
import com.speaive.blog.application.port.out.ai.AiCommentGenerationPort;
import com.speaive.blog.application.port.out.ai.AiSummaryGenerationPort;
import com.speaive.blog.application.port.out.importing.MarkdownImportLedger;
import com.speaive.blog.application.port.out.markdown.MarkdownPort;
import com.speaive.blog.application.port.out.media.MediaStoragePort;
import com.speaive.blog.application.port.out.persistence.AuthorRepository;
import com.speaive.blog.application.port.out.persistence.AccountRepository;
import com.speaive.blog.application.port.out.persistence.AgentRepository;
import com.speaive.blog.application.port.out.persistence.AgentRunRepository;
import com.speaive.blog.application.port.out.persistence.CommentRepository;
import com.speaive.blog.application.port.out.persistence.PostRepository;
import com.speaive.blog.application.port.out.persistence.NovelFragmentRepository;
import com.speaive.blog.application.port.out.persistence.CreativeWorkspaceRepository;
import com.speaive.blog.application.port.out.persistence.ContentRevisionRepository;
import com.speaive.blog.application.port.out.persistence.PostAiSummaryRepository;
import com.speaive.blog.application.port.out.persistence.InvitationRepository;
import com.speaive.blog.application.port.out.persistence.CommunityPostPolicyRepository;
import com.speaive.blog.application.port.out.persistence.CommunityCommentJobRepository;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.application.service.MarkdownApplicationService;
import com.speaive.blog.application.service.MediaApplicationService;
import com.speaive.blog.application.service.PostApplicationService;
import com.speaive.blog.application.service.NovelFragmentApplicationService;
import com.speaive.blog.application.service.AgentApplicationService;
import com.speaive.blog.application.service.CommentApplicationService;
import com.speaive.blog.application.service.MemberAccountApplicationService;
import com.speaive.blog.application.service.CommunityAutomationPlanner;
import com.speaive.blog.application.service.CommunityAutomationApplicationService;
import com.speaive.blog.application.service.CreativeWorkspaceApplicationService;
import com.speaive.blog.infrastructure.content.config.ContentStorageSettings;
import com.speaive.blog.infrastructure.content.markdown.CommonMarkMarkdownAdapter;
import com.speaive.blog.infrastructure.content.media.PostgresMediaStorageAdapter;
import com.speaive.blog.infrastructure.content.persistence.ledger.PostgresMarkdownImportLedger;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogAuthorDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogAgentDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogAgentRunDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogCommentDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogMediaDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogPostDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogNovelFragmentDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogPostAiSummaryDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogAccountDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogInvitationDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogCommunityPostPolicyDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogCommunityCommentJobDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogCreativeDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.MarkdownImportDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.BlogPersistenceMapStructMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.NovelFragmentPersistenceMapStructMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.BlogAiPersistenceMapStructMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.CreativePersistenceMapStructMapper;
import com.speaive.blog.infrastructure.content.persistence.repository.PostgresAgentRepository;
import com.speaive.blog.infrastructure.content.persistence.repository.PostgresAgentRunRepository;
import com.speaive.blog.infrastructure.content.persistence.repository.PostgresCommentRepository;
import com.speaive.blog.infrastructure.content.persistence.repository.PostgresAuthorRepository;
import com.speaive.blog.infrastructure.content.persistence.repository.PostgresPostRepository;
import com.speaive.blog.infrastructure.content.persistence.repository.PostgresNovelFragmentRepository;
import com.speaive.blog.infrastructure.content.persistence.repository.PostgresPostAiSummaryRepository;
import com.speaive.blog.infrastructure.content.persistence.repository.PostgresAccountRepository;
import com.speaive.blog.infrastructure.content.persistence.repository.PostgresInvitationRepository;
import com.speaive.blog.infrastructure.content.persistence.repository.PostgresCommunityPostPolicyRepository;
import com.speaive.blog.infrastructure.content.persistence.repository.PostgresCommunityCommentJobRepository;
import com.speaive.blog.infrastructure.content.persistence.repository.PostgresCreativeWorkspaceRepository;
import com.speaive.blog.infrastructure.content.persistence.repository.PostgresContentRevisionRepository;
import com.speaive.blog.infrastructure.transaction.SpringTransactionRunner;
import com.speaive.blog.interfaces.importing.MarkdownInboxImporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Path;
import java.time.Clock;

/**
 * 应用组合根：把出站适配器注入用例，并把用例暴露为 Spring Bean。此处可以同时认识内外层，Controller 不能照搬这里的直接依赖。
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class BlogBackendConfiguration {

    @Bean
    LocalVisitorContextAdapter visitorContext(@Value("${speaive.analytics.geo-directory}") Path directory) throws Exception {
        return new LocalVisitorContextAdapter(directory);
    }

    @Bean
    ArticleVisitRepository articleVisitRepository(JdbcTemplate jdbc, ArticleVisitPersistenceMapper mapping) {
        return new PostgresArticleVisitRepository(jdbc, mapping);
    }

    @Bean
    VisitRequestGuard visitRequestGuard(@Value("${speaive.analytics.trust-proxy-headers:false}") boolean trustProxy,
            @Value("${speaive.analytics.trusted-proxies:127.0.0.0/8,::1/128,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16}") String proxies) {
        return new VisitRequestGuard(trustProxy, proxies, Clock.systemUTC());
    }

    @Bean
    VisitAnalyticsApplicationService visitAnalyticsApplicationService(PostRepository posts, ArticleVisitRepository visits,
            VisitorContextPort context, TransactionRunner transactions,
            @Value("${speaive.analytics.retention-days:90}") int retentionDays,
            @Value("${speaive.analytics.enabled:true}") boolean enabled) {
        return new VisitAnalyticsApplicationService(posts, visits, context, transactions, Clock.systemUTC(), retentionDays, enabled);
    }

    @Bean
    ContentStorageSettings contentStorageSettings(
            @Value("${speaive.content.data-directory}") Path dataDirectory,
            @Value("${speaive.content.max-markdown-bytes:1048576}") long maxMarkdownBytes,
            @Value("${speaive.content.max-image-bytes:8388608}") long maxImageBytes) {
        return new ContentStorageSettings(dataDirectory, maxMarkdownBytes, maxImageBytes);
    }

    @Bean
    TransactionRunner transactionRunner(PlatformTransactionManager transactionManager) {
        return new SpringTransactionRunner(transactionManager);
    }

    @Bean
    PostRepository postRepository(
            BlogPostDatabaseMapper posts,
            BlogAuthorDatabaseMapper authors,
            BlogMediaDatabaseMapper media,
            BlogPersistenceMapStructMapper mapping) {
        return new PostgresPostRepository(posts, authors, media, mapping);
    }

    @Bean
    NovelFragmentRepository novelFragmentRepository(
            BlogNovelFragmentDatabaseMapper fragments,
            BlogAuthorDatabaseMapper authors,
            NovelFragmentPersistenceMapStructMapper mapping) {
        return new PostgresNovelFragmentRepository(fragments, authors, mapping);
    }

    @Bean
    CreativeWorkspaceRepository creativeWorkspaceRepository(
            BlogCreativeDatabaseMapper database,
            CreativePersistenceMapStructMapper mapping) {
        return new PostgresCreativeWorkspaceRepository(database, mapping);
    }

    @Bean
    ContentRevisionRepository contentRevisionRepository(
            BlogCreativeDatabaseMapper database,
            CreativePersistenceMapStructMapper mapping) {
        return new PostgresContentRevisionRepository(database, mapping);
    }

    @Bean
    AuthorRepository authorRepository(
            BlogAuthorDatabaseMapper authors,
            BlogPersistenceMapStructMapper mapping) {
        return new PostgresAuthorRepository(authors, mapping);
    }

    @Bean
    AgentRepository agentRepository(
            BlogAgentDatabaseMapper agents,
            BlogAuthorDatabaseMapper authors,
            BlogAiPersistenceMapStructMapper mapping) {
        return new PostgresAgentRepository(agents, authors, mapping);
    }

    @Bean
    AccountRepository accountRepository(
            BlogAccountDatabaseMapper accounts,
            BlogAuthorDatabaseMapper authors,
            BlogAiPersistenceMapStructMapper mapping) {
        return new PostgresAccountRepository(accounts, authors, mapping);
    }

    @Bean
    InvitationRepository invitationRepository(BlogInvitationDatabaseMapper invitations) {
        return new PostgresInvitationRepository(invitations);
    }

    @Bean
    CommunityPostPolicyRepository communityPostPolicyRepository(
            BlogCommunityPostPolicyDatabaseMapper policies,
            BlogAiPersistenceMapStructMapper mapping) {
        return new PostgresCommunityPostPolicyRepository(policies, mapping);
    }

    @Bean
    CommunityCommentJobRepository communityCommentJobRepository(
            BlogCommunityCommentJobDatabaseMapper jobs,
            BlogAiPersistenceMapStructMapper mapping) {
        return new PostgresCommunityCommentJobRepository(jobs, mapping);
    }

    @Bean
    CommentRepository commentRepository(
            BlogCommentDatabaseMapper comments,
            BlogAuthorDatabaseMapper authors,
            BlogAiPersistenceMapStructMapper mapping) {
        return new PostgresCommentRepository(comments, authors, mapping);
    }

    @Bean
    AgentRunRepository agentRunRepository(
            BlogAgentRunDatabaseMapper runs,
            BlogAiPersistenceMapStructMapper mapping) {
        return new PostgresAgentRunRepository(runs, mapping);
    }

    @Bean
    PostAiSummaryRepository postAiSummaryRepository(
            BlogPostAiSummaryDatabaseMapper summaries,
            BlogAiPersistenceMapStructMapper mapping) {
        return new PostgresPostAiSummaryRepository(summaries, mapping);
    }

    @Bean
    MarkdownPort markdownPort(ContentStorageSettings settings) {
        return new CommonMarkMarkdownAdapter(settings);
    }

    @Bean
    MarkdownImportLedger markdownImportLedger(MarkdownImportDatabaseMapper imports) {
        return new PostgresMarkdownImportLedger(imports);
    }

    @Bean
    MediaStoragePort mediaStoragePort(
            BlogMediaDatabaseMapper media,
            ContentStorageSettings settings,
            TransactionRunner transactions) {
        return new PostgresMediaStorageAdapter(media, settings, transactions);
    }

    @Bean
    PostApplicationService postApplicationService(
            PostRepository posts,
            AuthorRepository authors,
            MarkdownPort markdown,
            TransactionRunner transactions,
            CommunityAutomationPlanner communityAutomation) {
        return new PostApplicationService(
                posts,
                authors,
                markdown,
                transactions,
                Clock.systemUTC(),
                communityAutomation
        );
    }

    @Bean
    NovelFragmentApplicationService novelFragmentApplicationService(
            NovelFragmentRepository fragments,
            AuthorRepository authors,
            MarkdownPort markdown,
            TransactionRunner transactions) {
        return new NovelFragmentApplicationService(
                fragments, authors, markdown, transactions, Clock.systemUTC());
    }

    @Bean
    CreativeWorkspaceApplicationService creativeWorkspaceApplicationService(
            CreativeWorkspaceRepository workspace,
            ContentRevisionRepository revisions,
            PostRepository posts,
            NovelFragmentRepository novels,
            AgentRepository agents,
            CommentRepository comments,
            AiCommentGenerationPort ai,
            MarkdownPort markdown,
            TransactionRunner transactions) {
        return new CreativeWorkspaceApplicationService(
                workspace, revisions, posts, novels, agents, comments, ai, markdown,
                transactions, Clock.systemUTC());
    }

    @Bean
    CommunityAutomationPlanner communityAutomationPlanner(
            AgentRepository agents,
            CommunityPostPolicyRepository policies,
            CommunityCommentJobRepository jobs,
            @Value("${speaive.ai.community-max-agents-per-post:3}") int maxAgentsPerPost) {
        return new CommunityAutomationPlanner(
                agents, policies, jobs, Clock.systemUTC(), maxAgentsPerPost);
    }

    @Bean
    AgentApplicationService agentApplicationService(
            AgentRepository agents,
            AccountRepository accounts,
            AiCommentGenerationPort ai,
            TransactionRunner transactions,
            @Value("${speaive.ai.community-max-agents-per-account:3}") int maxAgentsPerAccount) {
        return new AgentApplicationService(
                agents, accounts, ai, transactions, Clock.systemUTC(), maxAgentsPerAccount);
    }

    @Bean
    MemberAccountApplicationService memberAccountApplicationService(
            AccountRepository accounts,
            InvitationRepository invitations,
            TransactionRunner transactions) {
        return new MemberAccountApplicationService(accounts, invitations, transactions, Clock.systemUTC());
    }

    @Bean
    CommentApplicationService commentApplicationService(
            PostRepository posts,
            PostAiSummaryRepository summaries,
            AgentRepository agents,
            CommentRepository comments,
            AgentRunRepository runs,
            AiCommentGenerationPort commentAi,
            AiSummaryGenerationPort summaryAi,
            TransactionRunner transactions) {
        return new CommentApplicationService(
                posts, summaries, agents, comments, runs, commentAi, summaryAi, transactions, Clock.systemUTC());
    }

    @Bean
    AiGenerationTaskRunner aiGenerationTaskRunner(
            @Value("${speaive.ai.comment-batch-concurrency:3}") int concurrency,
            @Value("${speaive.ai.comment-batch-capacity:30}") int capacity) {
        return new VirtualThreadAiGenerationTaskRunner(concurrency, capacity);
    }

    @Bean
    CommentBatchApplicationService commentBatchApplicationService(CommentUseCase comments,
            PostRepository posts, AgentRepository agents, TransactionRunner transactions,
            AiGenerationTaskRunner runner) {
        return new CommentBatchApplicationService(comments, posts, agents, transactions, runner);
    }

    @Bean
    CommunityAutomationApplicationService communityAutomationApplicationService(
            PostRepository posts,
            CommunityPostPolicyRepository policies,
            CommunityCommentJobRepository jobs,
            CommunityAutomationPlanner planner,
            CommentUseCase comments,
            TransactionRunner transactions,
            @Value("${speaive.ai.community-max-attempts:3}") int maxAttempts) {
        return new CommunityAutomationApplicationService(
                posts, policies, jobs, planner, comments, transactions, Clock.systemUTC(), maxAttempts);
    }

    @Bean
    MarkdownApplicationService markdownApplicationService(
            PostRepository posts,
            AuthorRepository authors,
            MarkdownPort markdown,
            MarkdownImportLedger imports,
            TransactionRunner transactions) {
        return new MarkdownApplicationService(
                posts,
                authors,
                markdown,
                imports,
                transactions,
                Clock.systemUTC()
        );
    }

    @Bean
    MediaApplicationService mediaApplicationService(MediaStoragePort media) {
        return new MediaApplicationService(media);
    }

    @Bean
    @ConditionalOnProperty(name = "speaive.content.import-enabled", matchIfMissing = true)
    MarkdownInboxImporter markdownInboxImporter(
            @Value("${speaive.content.import-directory}") Path importDirectory,
            @Value("${speaive.content.max-markdown-bytes:1048576}") long maxMarkdownBytes,
            MarkdownInboxUseCase imports) {
        return new MarkdownInboxImporter(importDirectory, maxMarkdownBytes, imports);
    }
}
