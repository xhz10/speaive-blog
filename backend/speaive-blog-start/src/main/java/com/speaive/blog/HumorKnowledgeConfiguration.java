package com.speaive.blog;

import com.speaive.blog.application.port.out.knowledge.HumorSearchPort;
import com.speaive.blog.application.port.out.persistence.HumorMaterialRepository;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.application.service.HumorKnowledgeApplicationService;
import com.speaive.blog.infrastructure.knowledge.persistence.mapping.HumorPersistenceMapStructMapper;
import com.speaive.blog.infrastructure.knowledge.persistence.repository.PostgresHumorMaterialRepository;
import com.speaive.blog.infrastructure.knowledge.search.LuceneHumorSearchAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Clock;

@Configuration(proxyBeanMethods=false)
class HumorKnowledgeConfiguration {
    @Bean HumorMaterialRepository humorMaterialRepository(JdbcTemplate jdbc, HumorPersistenceMapStructMapper mapper) {
        return new PostgresHumorMaterialRepository(jdbc, mapper);
    }
    @Bean(destroyMethod="close") LuceneHumorSearchAdapter humorSearchPort(HumorMaterialRepository materials) {
        return new LuceneHumorSearchAdapter(materials);
    }
    @Bean HumorKnowledgeApplicationService humorKnowledgeApplicationService(HumorMaterialRepository materials,
            HumorSearchPort search, TransactionRunner transactions) {
        return new HumorKnowledgeApplicationService(materials, search, transactions, Clock.systemUTC());
    }
}
