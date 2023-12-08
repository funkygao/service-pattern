package io.github.workload.overloading;

import io.github.workload.annotations.NotThreadSafe;
import io.github.workload.annotations.VisibleForTesting;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 基于(时间周期，请求数量周期)的滚动窗口.
 */
@NotThreadSafe
class MetricsRollingWindow {
    @VisibleForTesting
    static final long NS_PER_MS =  TimeUnit.MILLISECONDS.toNanos(1);
    @VisibleForTesting
    static final long DEFAULT_TIME_CYCLE_NS = TimeUnit.SECONDS.toNanos(1); // 1s
    @VisibleForTesting
    static final int DEFAULT_REQUEST_CYCLE = 2 << 10; // 2K

    /**
     * 时间周期.
     */
    @Getter(AccessLevel.PACKAGE)
    private final long timeCycleNs;

    /**
     * 请求数量周期.
     */
    private final int requestCycle;

    /**
     * 当前窗口开始时间点.
     */
    private volatile long startNs;

    /**
     * 当前窗口的请求总量.
     */
    private AtomicInteger requestCounter = new AtomicInteger(0);

    /**
     * 当前窗口的放行请求总量.
     */
    private AtomicInteger admittedCounter = new AtomicInteger(0);

    /**
     * 当前窗口所有请求的总排队时长.
     */
    private AtomicLong accumulatedQueuedNs = new AtomicLong(0);

    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock writeLock;
    private final Lock readLock;

    MetricsRollingWindow(long startNs) {
        this(DEFAULT_TIME_CYCLE_NS, DEFAULT_REQUEST_CYCLE, startNs);
    }

    private MetricsRollingWindow(long timeCycleNs, int requestCycle, long startNs) {
        this.timeCycleNs = timeCycleNs;
        this.requestCycle = requestCycle;
        this.startNs = startNs;
        this.writeLock = rwLock.writeLock();
        this.readLock = rwLock.readLock();
    }

    void tick(boolean admitted) {
        requestCounter.incrementAndGet();
        if (admitted) {
            admittedCounter.getAndIncrement();
        }
    }

    boolean full(long nowNs) {
        return nowNs - startNs > timeCycleNs // 时间满足
                || requestCounter.get() > requestCycle; // 请求数量满足
    }

    int admitted() {
        return admittedCounter.get();
    }

    void restart(long nowNs) {
        startNs = nowNs;
        requestCounter.set(0);
        admittedCounter.set(0);
        accumulatedQueuedNs.set(0);
    }

    void addWaitingNs(long waitingNs) {
        accumulatedQueuedNs.addAndGet(waitingNs);
    }

    long avgQueuedMs() {
        int requests = requestCounter.get();
        if (requests == 0) {
            // avoid divide by zero
            return 0;
        }

        return accumulatedQueuedNs.get() / (requests * NS_PER_MS);
    }
}
