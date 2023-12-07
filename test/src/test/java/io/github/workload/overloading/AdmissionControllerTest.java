package io.github.workload.overloading;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;

class AdmissionControllerTest {

    @RepeatedTest(10)
    @Execution(ExecutionMode.CONCURRENT)
    void getInstance() {
        AdmissionController controller = AdmissionController.getInstance("foo");
        assertTrue(controller instanceof FairSafeAdmissionController);
        assertSame(controller, AdmissionController.getInstance("foo"));
        assertNotSame(controller, AdmissionController.getInstance("bar"));
    }
}
