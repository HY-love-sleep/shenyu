package org.apache.shenyu.springboot.starter.plugin.sec.content;


import org.apache.shenyu.plugin.api.ShenyuPlugin;
import org.apache.shenyu.plugin.base.handler.PluginDataHandler;
import org.apache.shenyu.plugin.sec.content.ContentSecurityPlugin;
import org.apache.shenyu.plugin.sec.content.handler.ContentSecurityPluginDataHandler;
import org.apache.shenyu.plugin.sec.content.checker.ContentSecurityCheckerFactory;
import org.apache.shenyu.plugin.sec.content.ContentSecurityService;
import org.apache.shenyu.plugin.sec.content.checker.ContentSecurityChecker;
import org.apache.shenyu.plugin.sec.content.checker.ContentSecurityCheckerZkrj;
import org.apache.shenyu.plugin.sec.content.checker.ContentSecurityCheckerSm;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.codec.ServerCodecConfigurer;
import java.util.List;
import java.util.Arrays;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/7/3 17:01
 */
@Configuration
@ConditionalOnProperty(value = "shenyu.plugins.content-security.enabled", havingValue = "true", matchIfMissing = true)
public class ContentSecurityPluginConfiguration {

    @Bean
    public ContentSecurityCheckerZkrj contentSecurityCheckerZkrj() {
        return new ContentSecurityCheckerZkrj();
    }

    @Bean
    public ContentSecurityCheckerSm contentSecurityCheckerSm() {
        return new ContentSecurityCheckerSm();
    }

    @Bean
    @DependsOn({"contentSecurityCheckerZkrj", "contentSecurityCheckerSm"})
    public ContentSecurityCheckerFactory contentSecurityCheckerFactory() {
        ContentSecurityCheckerZkrj zkrjChecker = contentSecurityCheckerZkrj();
        ContentSecurityCheckerSm smChecker = contentSecurityCheckerSm();
        List<ContentSecurityChecker> checkers = Arrays.asList(zkrjChecker, smChecker);
        return new ContentSecurityCheckerFactory(checkers);
    }

    @Bean
    @DependsOn("contentSecurityCheckerFactory")
    public ContentSecurityService contentSecurityService() {
        return new ContentSecurityService(contentSecurityCheckerFactory());
    }

    @Bean
    @DependsOn("contentSecurityService")
    public ShenyuPlugin contentSecurityPlugin(final ServerCodecConfigurer configurer) {
        return new ContentSecurityPlugin(configurer.getReaders(), contentSecurityService());
    }

    @Bean
    public PluginDataHandler contentSecurityPluginDataHandler() {
        return new ContentSecurityPluginDataHandler();
    }
}
