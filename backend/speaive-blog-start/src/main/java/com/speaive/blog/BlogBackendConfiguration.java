package com.speaive.blog;

import com.speaive.blog.application.port.in.importing.MarkdownInboxUseCase;
import com.speaive.blog.application.port.out.importing.MarkdownImportLedger;
import com.speaive.blog.application.port.out.markdown.MarkdownPort;
import com.speaive.blog.application.port.out.media.MediaStoragePort;
import com.speaive.blog.application.port.out.persistence.AuthorRepository;
import com.speaive.blog.application.port.out.persistence.PostRepository;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.application.service.MarkdownApplicationService;
import com.speaive.blog.application.service.MediaApplicationService;
import com.speaive.blog.application.service.PostApplicationService;
import com.speaive.blog.infrastructure.content.config.ContentStorageSettings;
import com.speaive.blog.infrastructure.content.markdown.CommonMarkMarkdownAdapter;
import com.speaive.blog.infrastructure.content.media.PostgresMediaStorageAdapter;
import com.speaive.blog.infrastructure.content.persistence.ledger.PostgresMarkdownImportLedger;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogAuthorDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogMediaDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogPostDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.MarkdownImportDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.BlogPersistenceMapStructMapper;
import com.speaive.blog.infrastructure.content.persistence.repository.PostgresAuthorRepository;
import com.speaive.blog.infrastructure.content.persistence.repository.PostgresPostRepository;
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
            BlogMediaDatabaseMapper media,
            BlogPersistenceMapStructMapper mapping) {
        return new PostgresPostRepository(posts, authors, media, mapping);
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
    PostApplicationService postApplicationService(
            PostRepository posts,
            AuthorRepository authors,
            MarkdownPort markdown,
            TransactionRunner transactions) {
        return new PostApplicationService(
                posts,
                authors,
                markdown,
                transactions,
                Clock.systemUTC()
        );
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
