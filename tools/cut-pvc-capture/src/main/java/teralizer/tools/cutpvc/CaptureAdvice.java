package teralizer.tools.cutpvc;

import net.bytebuddy.asm.Advice;

/**
 * Inlined into every matched method under test. References only the
 * bootstrap-visible {@link CaptureLog}.
 */
public final class CaptureAdvice {

    private CaptureAdvice() {}

    @Advice.OnMethodEnter
    public static void enter(
        @Advice.Origin("#t") String typeName,
        @Advice.Origin("#m") String methodName,
        @Advice.AllArguments Object[] args
    ) {
        CaptureLog.record(typeName, methodName, args);
    }
}
