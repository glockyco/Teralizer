package teralizer;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.PropertyListenerAdapter;
import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.symbc.numeric.Constraint;
import gov.nasa.jpf.symbc.numeric.Expression;
import gov.nasa.jpf.symbc.numeric.PathCondition;
import gov.nasa.jpf.util.MethodSpec;
import gov.nasa.jpf.vm.MethodInfo;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.VM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.transformer.ModelToJsonTransformer;
import teralizer.transformer.SpfToModelTransformer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestGeneralizationListener extends PropertyListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestGeneralizationListener.class);

    private final MethodSpec testedMethodSpec;
    private final Path inputSpecificationPath;
    private final Path outputSpecificationPath;

    public TestGeneralizationListener(Config config) {
        this.testedMethodSpec = MethodSpec.createMethodSpec(config.getString("test_generalization.tested_method"));
        this.inputSpecificationPath = Paths.get(config.getString("test_generalization.input_specification_path"));
        this.outputSpecificationPath = Paths.get(config.getString("test_generalization.output_specification_path"));
    }

    @Override
    public void searchConstraintHit(Search search) {
        if (search.getDepth() >= search.getDepthLimit()) {
            throw new RuntimeException("Failed to collect input/output specification due to depth limiting. Depth limit of " + search.getDepthLimit() + " exceeded.");
        }
    }

    @Override
    public void searchFinished(Search search) {
        if (search.hasErrors()) {
            return; // Nothing to do here. Errors are handled by the JpfExecutionTask.
        }

        if (!Files.exists(this.inputSpecificationPath) || !Files.exists(this.outputSpecificationPath)) {
            throw new RuntimeException("Failed to collect input/output specification for unknown reason.");
        }
    }

    @Override
    public void propertyViolated(Search search) {
        String errorDetails = search.getLastError().getDetails();
        if (errorDetails.contains("java.lang.NullPointerException") && errorDetails.contains("at java.util.concurrent.atomic")) {
            throw new RuntimeException("Failed JPF execution due to incomplete native peers.\n\n" + errorDetails);
        }
    }

    @Override
    public void methodEntered(VM vm, ThreadInfo currentThread, MethodInfo enteredMethod) {
        if (this.testedMethodSpec.matches(enteredMethod)) {
            LOGGER.atDebug().log("Entering tested method: " + enteredMethod.toString());
        }
    }

    @Override
    public void methodExited(VM vm, ThreadInfo currentThread, MethodInfo exitedMethod) {
        if (this.testedMethodSpec.matches(exitedMethod)) {
            LOGGER.atDebug().log("Exiting tested method: " + exitedMethod.toString());
            this.writeSpecificationFiles(vm);
        }
    }

    private void writeSpecificationFiles(VM vm) {
        PathCondition pathCondition = PathCondition.getPC(vm);
        Constraint spfInput = pathCondition == null ? null : PathCondition.getPC(vm).header;
        // @TODO: Add thrown exceptions to the reported output specification.
        Expression spfOutput = (Expression) vm.getCurrentThread().getTopFrame().getOperandAttr();

        LOGGER.atDebug().log("Returning from: " + this.testedMethodSpec.getSource());
        LOGGER.atDebug().log("Input: " + (spfInput == null ? null : spfInput.toString()));
        LOGGER.atDebug().log("Output: " + (spfOutput == null ? null : spfOutput.toString()));

        SpfToModelTransformer spfToModelTransformer = new SpfToModelTransformer();
        ModelToJsonTransformer modelToJsonTransformer = new ModelToJsonTransformer();

        teralizer.domain.Expression modelInput = spfToModelTransformer.transform(spfInput);
        teralizer.domain.Expression modelOutput = spfToModelTransformer.transform(spfOutput);

        String jsonInput = modelToJsonTransformer.transform(modelInput);
        String jsonOutput = modelToJsonTransformer.transform(modelOutput);

        try {
            Files.write(this.inputSpecificationPath, jsonInput.getBytes());
            Files.write(this.outputSpecificationPath, jsonOutput.getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
