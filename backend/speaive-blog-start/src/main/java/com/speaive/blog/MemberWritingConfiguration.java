package com.speaive.blog;

import com.speaive.blog.application.port.in.account.WritingAccountUseCase;
import com.speaive.blog.application.port.in.post.MemberWritingUseCase;
import com.speaive.blog.application.port.out.persistence.*;
import com.speaive.blog.application.port.out.security.ContentEncryptionPort;
import com.speaive.blog.application.port.out.markdown.MarkdownPort;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.application.service.*;
import com.speaive.blog.infrastructure.security.AesGcmContentEncryptionAdapter;
import com.speaive.blog.infrastructure.content.persistence.mapper.MemberPostDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.MemberPersistenceMapStructMapper;
import com.speaive.blog.infrastructure.content.persistence.repository.PostgresMemberPostRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import java.nio.file.Path;
import java.time.Clock;

/** 会员写作组合根，密钥文件独立于内容目录和数据库备份。 */
@Configuration
public class MemberWritingConfiguration {
    @Bean
    ContentEncryptionPort contentEncryptionPort(@Value("${speaive.content.encryption-key-file:../.secrets/content-keys.properties}") String keyFile) {
        return new AesGcmContentEncryptionAdapter(Path.of(keyFile).toAbsolutePath().normalize());
    }
    @Bean
    MemberPostRepository memberPostRepository(MemberPostDatabaseMapper database, MemberPersistenceMapStructMapper mapping,
            AccountRepository accounts, ContentEncryptionPort encryption) {
        return new PostgresMemberPostRepository(database, mapping, accounts, encryption);
    }
    @Bean
    WritingAccountUseCase writingAccountUseCase(AccountRepository accounts, MemberPostRepository posts,
            ContentEncryptionPort encryption, TransactionRunner transactions) {
        return new WritingAccountApplicationService(accounts, posts, encryption, transactions, Clock.systemUTC());
    }
    @Bean
    MemberWritingUseCase memberWritingUseCase(AccountRepository accounts, MemberPostRepository posts,
            MarkdownPort markdown, TransactionRunner transactions) {
        return new MemberWritingApplicationService(accounts, posts, markdown, transactions, Clock.systemUTC());
    }
}
