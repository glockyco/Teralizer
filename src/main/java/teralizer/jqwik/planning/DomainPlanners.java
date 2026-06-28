package teralizer.jqwik.planning;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class DomainPlanners {
    public static final List<DomainPlanner> REGISTERED =
        Collections.unmodifiableList(Arrays.asList(new NumericDomainPlanner(), new BooleanDomainPlanner()));

    private DomainPlanners() {}
}
