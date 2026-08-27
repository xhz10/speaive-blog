package com.speaive.blog.infrastructure.ai;

import com.speaive.blog.application.port.out.ai.AiCommentGenerationPort;
import com.speaive.blog.application.port.out.ai.AiSummaryGenerationPort;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AiAdapterConfiguration {
    @Bean
    AiCommentGenerationPort aiCommentGenerationPort(
            ObjectProvider<ChatModel> models,
            @Value("${speaive.ai.enabled:false}") boolean enabled,
            @Value("${speaive.ai.max-article-characters:24000}") int maxArticleCharacters) {
        ChatModel model = models.getIfAvailable();
        if (!enabled || model == null) {
            return new DisabledAiCommentGenerationAdapter();
        }
        return new SpringAiCommentGenerationAdapter(model, maxArticleCharacters);
    }

    @Bean
    AiSummaryGenerationPort aiSummaryGenerationPort(
            ObjectProvider<ChatModel> models,
            @Value("${speaive.ai.enabled:false}") boolean enabled,
            @Value("${speaive.ai.max-article-characters:24000}") int maxArticleCharacters) {
        ChatModel model = models.getIfAvailable();
        if (!enabled || model == null) {
            return new DisabledAiSummaryGenerationAdapter();
        }
        return new SpringAiSummaryGenerationAdapter(model, maxArticleCharacters);
    }
}
