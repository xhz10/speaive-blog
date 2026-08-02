package com.speaive.blog;

import com.speaive.blog.application.BlogApplicationService;
import com.speaive.blog.application.ContentStorePort;
import com.speaive.blog.infrastructure.content.BlogPersistenceMapper;
import com.speaive.blog.infrastructure.content.FileContentStoreSettings;
import com.speaive.blog.infrastructure.content.MarkdownInboxImporter;
import com.speaive.blog.infrastructure.content.PostgresContentStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class BlogBackendConfiguration {

    @Bean
    ContentStorePort contentStore(
            BlogPersistenceMapper mapper,
            @Value("${speaive.content.data-directory}") Path dataDirectory,
            @Value("${speaive.content.max-markdown-bytes:1048576}") long maxMarkdownBytes,
            @Value("${speaive.content.max-image-bytes:8388608}") long maxImageBytes) {
        return new PostgresContentStore(mapper, new FileContentStoreSettings(
                dataDirectory,
                maxMarkdownBytes,
                maxImageBytes
        ));
    }

    @Bean
    @ConditionalOnProperty(name = "speaive.content.import-enabled", matchIfMissing = true)
    MarkdownInboxImporter markdownInboxImporter(
            @Value("${speaive.content.import-directory}") Path importDirectory,
            @Value("${speaive.content.max-markdown-bytes:1048576}") long maxMarkdownBytes,
            ContentStorePort contentStore,
            BlogPersistenceMapper mapper,
            PlatformTransactionManager transactionManager) {
        return new MarkdownInboxImporter(importDirectory, maxMarkdownBytes, contentStore, mapper,
                new TransactionTemplate(transactionManager));
    }

    @Bean
    BlogApplicationService blogApplicationService(ContentStorePort contentStore) {
        return new BlogApplicationService(contentStore);
    }
}
