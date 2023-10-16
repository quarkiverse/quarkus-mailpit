package io.quarkiverse.mailpit.test;

import java.util.Collections;
import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceConfigurableLifecycleManager;

public class QuarkusMailpitManager implements QuarkusTestResourceConfigurableLifecycleManager<WithMailbox> {

    private WithMailbox options;
    private Mailbox mailbox;

    @Override
    public void init(WithMailbox annotation) {
        this.options = annotation;
        this.mailbox = new Mailbox();
    }

    @Override
    public void init(Map<String, String> initArgs) {
        throw new IllegalStateException("Use @WithMailbox() annotation instead");
    }

    @Override
    public Map<String, String> start() {
        return Collections.emptyMap();
    }

    @Override
    public void stop() {
        // nothing to stop
    }

    @Override
    public void inject(TestInjector testInjector) {
        testInjector.injectIntoFields(mailbox,
                new TestInjector.AnnotatedAndMatchesType(InjectMailbox.class, Mailbox.class));
    }
}