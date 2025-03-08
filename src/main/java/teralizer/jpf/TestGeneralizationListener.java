package teralizer.jpf;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.PropertyListenerAdapter;
import gov.nasa.jpf.jvm.bytecode.JVMReturnInstruction;
import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.symbc.numeric.Constraint;
import gov.nasa.jpf.symbc.numeric.Expression;
import gov.nasa.jpf.symbc.numeric.PathCondition;
import gov.nasa.jpf.util.MethodSpec;
import gov.nasa.jpf.vm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.domain.CapturedException;
import teralizer.transformer.ModelToJsonTransformer;
import teralizer.transformer.SpfToModelTransformer;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.sun.management.GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION;

public class TestGeneralizationListener extends PropertyListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestGeneralizationListener.class);

    private final String instrumentedMethodQualifiedName;
    private final MethodSpec instrumentedMethodSpec;
    private final MethodSpec testedMethodSpec;

    private final Path inputSpecificationPath;
    private final Path outputSpecificationPath;

    private final double maxExecutionTime;
    private final long maxPathConditionSize;

    private long startTime;
    private int recursionDepth;
    private final GcMonitor gcMonitor;

    public TestGeneralizationListener(Config config) {
        this.instrumentedMethodQualifiedName = config.getString("test_generalization.instrumented_method");
        this.instrumentedMethodSpec = MethodSpec.createMethodSpec(this.instrumentedMethodQualifiedName);
        this.testedMethodSpec = MethodSpec.createMethodSpec(config.getString("test_generalization.tested_method"));
        this.inputSpecificationPath = Paths.get(config.getString("test_generalization.input_specification_path"));
        this.outputSpecificationPath = Paths.get(config.getString("test_generalization.output_specification_path"));
        this.maxExecutionTime = config.getDouble("test_generalization.max_execution_time");
        this.maxPathConditionSize = config.getLong("test_generalization.max_path_condition_size");
        this.gcMonitor = new GcMonitor(config.getDouble("test_generalization.max_gc_overhead_percent", 20.0), this.instrumentedMethodQualifiedName);
    }

    @Override
    public void searchStarted(Search search) {
        this.startTime = System.currentTimeMillis();
        this.recursionDepth = -1;
        this.gcMonitor.start(this.startTime);
    }

    @Override
    public void searchFinished(Search search) {
        this.gcMonitor.stop();
    }

    @Override
    public void searchConstraintHit(Search search) {
        if (search.getDepth() >= search.getDepthLimit()) {
            throw new RuntimeException(this.instrumentedMethodQualifiedName + " - Failed to collect input/output specification due to depth limiting. Depth limit of " + search.getDepthLimit() + " exceeded.");
        }
    }

    @Override
    public void propertyViolated(Search search) {
        String errorDetails = search.getLastError().getDetails();
        if (errorDetails.contains("java.lang.NullPointerException") && errorDetails.contains("at java.util.concurrent.atomic")) {
            throw new RuntimeException(this.instrumentedMethodQualifiedName + " - Failed JPF execution due to incomplete native peers.\n\n" + errorDetails);
        }
    }

    @Override
    public void stateAdvanced(Search search) {
        this.checkExecutionTimeoutExceeded();
        this.checkPcSizeLimitExceeded(PathCondition.getPC(search.getVM()));
    }

    @Override
    public void methodEntered(VM vm, ThreadInfo currentThread, MethodInfo enteredMethod) {
        if (this.testedMethodSpec.matches(enteredMethod)) {
            LOGGER.atDebug().log("Entering tested method: " + enteredMethod.toString());
            this.recursionDepth++;
        }
    }

    @Override
    public void methodExited(VM vm, ThreadInfo currentThread, MethodInfo exitedMethod) {
        if (this.testedMethodSpec.matches(exitedMethod)) {
            LOGGER.atDebug().log("Exiting tested method: " + exitedMethod.toString());
            this.recursionDepth--;
        }
        if (this.instrumentedMethodSpec.matches(exitedMethod)) {
            this.writeSpecificationFiles(vm, currentThread);
            vm.getSearch().terminate();
        }
    }

    private void writeSpecificationFiles(VM vm, ThreadInfo currentThread) {
        PathCondition pathCondition = PathCondition.getPC(vm);
        this.checkPcSizeLimitExceeded(pathCondition);

        Constraint spfInput = pathCondition == null ? null : pathCondition.header;
        Instruction exitInstruction = vm.getCurrentThread().getPC();
        Expression spfOutput = null;
        CapturedException capturedException = null;

        LOGGER.atDebug().log("Returning from: " + this.testedMethodSpec.getSource());
        LOGGER.atDebug().log("Input: " + (spfInput == null ? null : spfInput.toString()));

        if (exitInstruction instanceof JVMReturnInstruction) {
            spfOutput = (Expression) ((JVMReturnInstruction) exitInstruction).getReturnAttr(vm.getCurrentThread());
            LOGGER.atDebug().log("Output: " + (spfOutput == null ? null : spfOutput.toString()));
        } else if (exitInstruction.getMethodInfo().getThrownExceptionClassNames().length > 0) {
            String exceptionClass = exitInstruction.getMethodInfo().getThrownExceptionClassNames()[0];
            ExceptionInfo pendingException = currentThread.getPendingException();
            String exceptionMessage = pendingException.getCauseDetails();
            capturedException = new CapturedException(exceptionClass, exceptionMessage);
            LOGGER.atDebug().log("Output: Exception thrown " + exceptionClass);
        } else {
            throw new RuntimeException("Unexpected exit instruction: " + exitInstruction);
        }

        SpfToModelTransformer spfToModelTransformer = new SpfToModelTransformer();
        ModelToJsonTransformer modelToJsonTransformer = new ModelToJsonTransformer();

        teralizer.domain.Expression modelInput = spfToModelTransformer.transform(spfInput);

        teralizer.domain.Expression modelOutput;

        if (capturedException == null) {
            modelOutput = spfToModelTransformer.transform(spfOutput);
        } else {
            modelOutput = spfToModelTransformer.transform(capturedException);
        }

        String jsonInput = modelToJsonTransformer.transform(modelInput);
        String jsonOutput = modelToJsonTransformer.transform(modelOutput);

        try {
            Files.write(this.inputSpecificationPath, jsonInput.getBytes());
            Files.write(this.outputSpecificationPath, jsonOutput.getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkExecutionTimeoutExceeded() {
        double elapsedTime = (System.currentTimeMillis() - this.startTime) / 1000.0;
        if (elapsedTime > this.maxExecutionTime) {
            throw new RuntimeException(this.instrumentedMethodQualifiedName + " - Execution timeout exceeded: " + elapsedTime + " of " + this.maxExecutionTime + " seconds passed.");
        }
    }

    private void checkPcSizeLimitExceeded(PathCondition pathCondition) {
        int pcLength = pathCondition == null ? 0 : pathCondition.toString().length();
        if (pcLength > this.maxPathConditionSize) {
            throw new RuntimeException(this.instrumentedMethodQualifiedName + " - PC size limit exceeded: " + pcLength + " of " + this.maxPathConditionSize + " characters used.");
        }
    }

    private static class GcMonitor implements NotificationListener {
        private static final Logger LOGGER = LoggerFactory.getLogger(GcMonitor.class);

        private final double maxGcOverheadPercent;
        private final String instrumentedMethodName;
        private final AtomicLong totalGcTimeMs = new AtomicLong(0);
        private final AtomicReference<Long> startTimeRef = new AtomicReference<>();
        private final MemoryMXBean memoryBean;

        public GcMonitor(double maxGcOverheadPercent, String instrumentedMethodName) {
            this.maxGcOverheadPercent = maxGcOverheadPercent;
            this.instrumentedMethodName = instrumentedMethodName;
            this.memoryBean = ManagementFactory.getMemoryMXBean();
        }

        public void start(long startTime) {
            this.startTimeRef.set(startTime);
            this.totalGcTimeMs.set(0);

            for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
                if (gcBean instanceof NotificationEmitter) {
                    ((NotificationEmitter) gcBean).addNotificationListener(this, null, null);
                    LOGGER.atDebug().log("Registered GC notification listener for {}.", gcBean.getName());
                }
            }
        }

        public void stop() {
            for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
                if (gcBean instanceof NotificationEmitter) {
                    try {
                        ((NotificationEmitter) gcBean).removeNotificationListener(this);
                    } catch (Exception e) {
                        LOGGER.atWarn().log("Failed to remove GC notification listener.", e);
                    }
                }
            }
        }

        @Override
        public void handleNotification(Notification notification, Object handback) {
            if (notification.getType().equals(GARBAGE_COLLECTION_NOTIFICATION)) {
                CompositeData userData = (CompositeData) notification.getUserData();
                CompositeData gcInfo = (CompositeData) userData.get("gcInfo");
                long duration = (Long) gcInfo.get("duration");

                this.totalGcTimeMs.addAndGet(duration);

                long startTime = this.startTimeRef.get();
                long elapsedTime = System.currentTimeMillis() - startTime;
                double gcOverheadPercentage = this.totalGcTimeMs.get() * 100.0 / elapsedTime;

                MemoryUsage heapUsage = this.memoryBean.getHeapMemoryUsage();
                long maxMemory = heapUsage.getMax();
                double memoryUsagePercentage = maxMemory > 0 ? (double) heapUsage.getUsed() / maxMemory * 100.0 : 0;

                LOGGER.atDebug().log(
                    "GC overhead: {}%, Memory usage: {}%",
                    String.format("%.2f", gcOverheadPercentage),
                    String.format("%.2f", memoryUsagePercentage)
                );

                if (gcOverheadPercentage > this.maxGcOverheadPercent) {
                    throw new RuntimeException(String.format(
                        "%s - GC overhead limit exceeded: %.2f%% of %.2f%% threshold. Memory usage: %.2f%%",
                        this.instrumentedMethodName,
                        gcOverheadPercentage,
                        this.maxGcOverheadPercent,
                        memoryUsagePercentage
                    ));
                }
            }
        }
    }
}
