package io.quarkiverse.mailpit.test;

import java.util.Collections;
import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceConfigurableLifecycleManager;

public class QuarkusMailpitManager implements QuarkusTestResourceConfigurableLifecycleManager<WithMailer> {

    private WithMailer options;
    private MailerContext mailerContext;

    @Override
    public void init(WithMailer annotation) {
        this.options = annotation;
    }

    @Override
    public void init(Map<String, String> initArgs) {
        throw new IllegalStateException("Use @WithMailer() annotation instead");
    }

    @Override
    public Map<String, String> start() {
        this.mailerContext = new MailerContext();
        return Collections.emptyMap();
    }

    @Override
    public void stop() {
        // nothing to stop
    }

    @Override
    public void inject(TestInjector testInjector) {
        testInjector.injectIntoFields(mailerContext,
                new TestInjector.AnnotatedAndMatchesType(InjectMailer.class, MailerContext.class));
    }
}
