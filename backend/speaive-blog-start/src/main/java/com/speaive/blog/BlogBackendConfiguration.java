package com.speaive.blog;

import com.speaive.blog.application.service.BlogApplicationService;
import com.speaive.blog.application.port.in.MarkdownInboxUseCase;
import com.speaive.blog.application.port.out.AuthorRepository;
import com.speaive.blog.application.port.out.MarkdownImportLedger;
import com.speaive.blog.application.port.out.MarkdownPort;
import com.speaive.blog.application.port.out.MediaStoragePort;
import com.speaive.blog.application.port.out.PostRepository;
import com.speaive.blog.application.port.out.TransactionRunner;
import com.speaive.blog.infrastructure.content.BlogAuthorDatabaseMapper;
import com.speaive.blog.infrastructure.content.BlogMediaDatabaseMapper;
import com.speaive.blog.infrastructure.content.BlogPersistenceMapStructMapper;
import com.speaive.blog.infrastructure.content.BlogPostDatabaseMapper;
import com.speaive.blog.infrastructure.content.CommonMarkMarkdownAdapter;
import com.speaive.blog.infrastructure.content.ContentStorageSettings;
import com.speaive.blog.infrastructure.content.MarkdownImportDatabaseMapper;
import com.speaive.blog.infrastructure.content.PostgresAuthorRepository;
import com.speaive.blog.infrastructure.content.PostgresMarkdownImportLedger;
import com.speaive.blog.infrastructure.content.PostgresMediaStorageAdapter;
import com.speaive.blog.infrastructure.content.PostgresPostRepository;
import com.speaive.blog.infrastructure.content.SpringTransactionRunner;
import com.speaive.blog.interfaces.importing.MarkdownInboxImporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Path;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class BlogBackendConfiguration {

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
            BlogPersistenceMapStructMapper mapping) {
        return new PostgresPostRepository(posts, authors, mapping);
    }

    @Bean
    AuthorRepository authorRepository(
            BlogAuthorDatabaseMapper authors,
            BlogPersistenceMapStructMapper mapping) {
        return new PostgresAuthorRepository(authors, mapping);
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
    BlogApplicationService blogApplicationService(
            PostRepository posts,
            AuthorRepository authors,
            MarkdownPort markdown,
            MediaStoragePort media,
            MarkdownImportLedger imports,
            TransactionRunner transactions) {
        return new BlogApplicationService(
                posts,
                authors,
                markdown,
                media,
                imports,
                transactions,
                Clock.systemUTC()
        );
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
