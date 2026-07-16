import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

public final class TestLauncher {
    public static void main(String[] args) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectPackage("com.github.blade.hvac"))
                .build();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        var launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);
        var summary = listener.getSummary();
        summary.printTo(new java.io.PrintWriter(System.out, true));
        summary.getFailures().forEach(failure -> {
            System.out.println("FAILED: " + failure.getTestIdentifier().getDisplayName());
            failure.getException().printStackTrace(System.out);
        });
        if (summary.getFailures().size() > 0 || summary.getTestsFailedCount() > 0)
            throw new AssertionError("Test suite failed");
    }
}
