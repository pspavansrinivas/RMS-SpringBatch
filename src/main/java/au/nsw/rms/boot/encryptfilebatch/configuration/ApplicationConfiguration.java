package au.nsw.rms.boot.encryptfilebatch.configuration;

import org.springframework.batch.core.scope.StepScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Created by pavan on 10/12/18.
 */
@Configuration
public class ApplicationConfiguration {

    @Bean
    public StepScope stepScope() {
        final StepScope stepScope = new StepScope();
        stepScope.setAutoProxy(true);
        return stepScope;
    }
}
