package io.github.workload.window;

import io.github.workload.overloading.WorkloadPriority;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static io.github.workload.window.WindowConfig.NS_PER_MS;

@Slf4j
public class TimeAndCountWindowState extends WindowState {
    /**
     * 窗口启动时间.
     *
     * 通过{@link System#nanoTime()}获取.
     */
    @Getter(AccessLevel.PACKAGE)
    private final long startNs;

    /**
     * 被准入的数量.
     */
    private final LongAdder admittedCounter;

    /**
     * 累计排队等待时长.
     */
    private final LongAdder accumulatedQueuedNs;

    private final ConcurrentSkipListMap<Integer /* priority */, AtomicInteger /* requested */> histogram;

    TimeAndCountWindowState(long startNs) {
        super();
        this.startNs = startNs;
        this.admittedCounter = new LongAdder();
        this.accumulatedQueuedNs = new LongAdder();
        this.histogram = new ConcurrentSkipListMap<>();
    }

    public ConcurrentSkipListMap<Integer, AtomicInteger> histogram() {
        return histogram;
    }

    /**
     * 窗口期内总计准入多少工作负荷.
     */
    public int admitted() {
        return admittedCounter.intValue();
    }

    public void waitNs(long waitingNs) {
        accumulatedQueuedNs.add(waitingNs);
    }

    long ageMs(long nowNs) {
        return (nowNs - startNs) / NS_PER_MS;
    }

    public long avgQueuedMs() {
        int requested = requested();
        if (requested == 0) {
            // avoid divide by zero
            return 0;
        }

        return accumulatedQueuedNs.longValue() / (requested * NS_PER_MS);
    }

    @Override
    protected void doSample(WorkloadPriority priority, boolean admitted) {
        if (admitted) {
            admittedCounter.increment();
        }
        AtomicInteger prioritizedCounter = histogram.computeIfAbsent(priority.P(), key -> new AtomicInteger(0));
        prioritizedCounter.incrementAndGet();
    }

    @Override
    void cleanup() {
        histogram.clear();
    }

    @Override
    void logSwapping(String prefix, long nowNs, WindowState nextWindow, WindowConfig config) {
        log.debug("[{}] after:{}ms, swapped window:{} -> {}, admitted:{}/{}, delta:{}",
                prefix, ageMs(nowNs),
                this.hashCode(), nextWindow.hashCode(),
                this.admitted(), this.requested(),
                this.requested() - config.getRequestCycle());
    }
}
