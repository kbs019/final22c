// MultiSmsConfig.java
package com.ex.final22c;

import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SmsProps.class)
public class MultiSmsConfig {

    @Bean(name = "solapiA")
    @ConditionalOnProperty(prefix = "sms.a", name = { "api-key", "api-secret-key", "domain", "from-number" })
    public DefaultMessageService solapiA(SmsProps props) {
        return NurigoApp.initialize(
                props.a.apiKey, props.a.apiSecretKey, props.a.domain);
    }

    @Bean(name = "solapiB")
    @ConditionalOnProperty(prefix = "sms.b", name = { "api-key", "api-secret-key", "domain", "from-number" })
    public DefaultMessageService solapiB(SmsProps props) {
        return NurigoApp.initialize(
                props.b.apiKey, props.b.apiSecretKey, props.b.domain);
    }
}
