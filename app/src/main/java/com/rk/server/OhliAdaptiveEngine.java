package com.rk.server;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Embedded, offline adaptive performance controller for OHLI's on-device server.
 * It learns a device-specific latency baseline from live requests (EWMA) and
 * adjusts bounded worker capacity using queue pressure and heap pressure.
 * This is a small runtime optimizer, not a generative/conversational LLM.
 */
public final class OhliAdaptiveEngine {
    private static final long MB = 1024L * 1024L;
    private static final int QUEUE_CAPACITY = 48;
    private static final int MAX_ROUTE_SAMPLES = 24;

    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong totalLatencyMs = new AtomicLong();
    private final AtomicInteger threadSequence = new AtomicInteger();
    private final Object modelLock = new Object();
    private final Object routeLock = new Object();
    private final LinkedHashMap<String, RouteMetric> routeMetrics = new LinkedHashMap<String, RouteMetric>();

    private volatile int memoryClassMb = 128;
    private volatile double latencyEwmaMs = 0.0d;
    private volatile double heapPressureEwma = 0.0d;
    private volatile double failureRateEwma = 0.0d;
    private volatile long lastTuneAt = 0L;
    private volatile long lastScaleDownAt = 0L;
    private volatile long rejectedAtLastTune = 0L;
    private volatile String mode = "BALANCED";
    // Tiny online logistic model. Weights are updated from measured queue/heap/latency outcomes.
    private final double[] modelWeights = new double[] { -2.20d, 3.00d, 1.20d, 3.30d, 1.50d, 2.10d };
    private volatile long modelSamples = 0L;
    private volatile double overloadRisk = 0.0d;
    private volatile long lastPersistedSamples = 0L;

    public ThreadPoolExecutor createExecutor(int deviceMemoryClassMb) {
        memoryClassMb = Math.max(48, deviceMemoryClassMb);
        final ThreadFactory factory = new ThreadFactory() {
            @Override public Thread newThread(Runnable task) {
                Thread t = new Thread(task, "OHLI-Worker-" + threadSequence.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };
        int maximum = safeThreadCap();
        return new ThreadPoolExecutor(2, maximum, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(QUEUE_CAPACITY), factory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    public void recordRequest(String route, long elapsedMs, boolean failed, ThreadPoolExecutor pool) {
        long safeLatency = Math.max(0L, elapsedMs);
        requests.incrementAndGet();
        totalLatencyMs.addAndGet(safeLatency);
        if (failed) failures.incrementAndGet();
        synchronized (modelLock) {
            latencyEwmaMs = latencyEwmaMs <= 0.0d
                    ? safeLatency : (latencyEwmaMs * 0.82d) + (safeLatency * 0.18d);
            // 500-class exceptions feed a smoothed failure signal into online learning.
            failureRateEwma = (failureRateEwma * 0.88d) + (failed ? 0.12d : 0.0d);
        }
        recordRoute(route, safeLatency, failed);
        tune(pool, false);
    }

    public void recordRejected(ThreadPoolExecutor pool) {
        rejected.incrementAndGet();
        tune(pool, true);
    }

    private void recordRoute(String route, long latencyMs, boolean failed) {
        String key = route == null || route.length() == 0 ? "/unknown" : route;
        if (key.length() > 80) key = key.substring(0, 80);
        synchronized (routeLock) {
            RouteMetric metric = routeMetrics.get(key);
            if (metric == null) {
                if (routeMetrics.size() >= MAX_ROUTE_SAMPLES) key = "/other";
                metric = routeMetrics.get(key);
                if (metric == null) {
                    metric = new RouteMetric();
                    routeMetrics.put(key, metric);
                }
            }
            metric.count++;
            metric.totalMs += latencyMs;
            metric.ewmaMs = metric.count == 1 ? latencyMs : metric.ewmaMs * 0.8d + latencyMs * 0.2d;
            if (failed) metric.failures++;
        }
    }

    /** Bounded feedback controller; it will not grow workers beyond device memory limits. */
    private void tune(ThreadPoolExecutor pool, boolean pressureEvent) {
        if (pool == null || pool.isShutdown()) return;
        long now = System.currentTimeMillis();
        if (!pressureEvent && now - lastTuneAt < 900L) return;
        synchronized (modelLock) {
            if (!pressureEvent && now - lastTuneAt < 900L) return;
            lastTuneAt = now;
            Runtime runtime = Runtime.getRuntime();
            long maxHeap = Math.max(1L, runtime.maxMemory());
            long usedHeap = Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
            double pressure = Math.min(1.0d, (double) usedHeap / (double) maxHeap);
            heapPressureEwma = heapPressureEwma <= 0.0d
                    ? pressure : (heapPressureEwma * 0.75d) + (pressure * 0.25d);

            int cap = safeThreadCap();
            int currentMax = pool.getMaximumPoolSize();
            int targetMax = Math.min(currentMax, cap);
            int queue = pool.getQueue().size();
            int active = pool.getActiveCount();
            long rejectedNow = rejected.get();
            boolean newRejects = rejectedNow > rejectedAtLastTune;
            rejectedAtLastTune = rejectedNow;
            double risk = learnOverloadRisk(queue, active, currentMax, heapPressureEwma,
                    latencyEwmaMs, pressureEvent || newRejects);

            if (heapPressureEwma >= 0.84d) {
                targetMax = Math.max(2, Math.min(cap, targetMax - 1));
                mode = "HEAP_GUARD";
            } else if (pressureEvent || newRejects || queue >= 12 || risk >= 0.72d) {
                targetMax = Math.min(cap, Math.max(targetMax + 1, Math.min(cap, active + 1)));
                mode = "AI_BOOST";
            } else if (queue >= 5 && (latencyEwmaMs >= 420.0d || risk >= 0.58d)) {
                targetMax = Math.min(cap, targetMax + 1);
                mode = "ADAPTIVE_BOOST";
            } else if (queue <= 1 && active <= 2 && latencyEwmaMs > 0.0d
                    && latencyEwmaMs < 300.0d && risk < 0.35d && now - lastScaleDownAt > 30000L) {
                targetMax = Math.max(3, targetMax - 1);
                lastScaleDownAt = now;
                mode = "ECO";
            } else {
                mode = queue >= 4 ? "BALANCING" : "BALANCED";
            }

            targetMax = Math.max(2, Math.min(cap, targetMax));
            int targetCore = Math.min(targetMax, queue >= 12 ? 3 : 2);
            try {
                if (targetMax < pool.getCorePoolSize()) pool.setCorePoolSize(targetCore);
                pool.setMaximumPoolSize(targetMax);
                if (targetCore != pool.getCorePoolSize()) pool.setCorePoolSize(targetCore);
            } catch (IllegalArgumentException ignored) {
                // A concurrent scale decision can only leave the previous safe bounds in place.
            }
        }
    }

    private double learnOverloadRisk(int queue, int active, int currentMax, double heapPressure,
                                     double latencyMs, boolean rejectedRecently) {
        double[] features = new double[] {
                1.0d,
                Math.min(1.0d, Math.max(0.0d, (double) queue / (double) QUEUE_CAPACITY)),
                Math.min(1.0d, Math.max(0.0d, currentMax <= 0 ? 0.0d : (double) active / (double) currentMax)),
                Math.min(1.0d, Math.max(0.0d, heapPressure)),
                Math.min(1.0d, Math.max(0.0d, latencyMs / 1500.0d)),
                Math.min(1.0d, Math.max(0.0d, failureRateEwma))
        };
        synchronized (modelLock) {
            double score = 0.0d;
            for (int i = 0; i < modelWeights.length; i++) score += modelWeights[i] * features[i];
            score = Math.max(-12.0d, Math.min(12.0d, score));
            double predicted = 1.0d / (1.0d + Math.exp(-score));
            boolean observedOverload = rejectedRecently || queue >= 8 || heapPressure >= 0.82d
                    || latencyMs >= 1200.0d || failureRateEwma >= 0.18d;
            double label = observedOverload ? 1.0d : 0.0d;
            double error = label - predicted;
            double rate = 0.045d;
            for (int i = 0; i < modelWeights.length; i++) {
                double decay = i == 0 ? 0.0d : 0.0008d * modelWeights[i];
                modelWeights[i] += rate * error * features[i] - decay;
            }
            modelSamples++;
            overloadRisk = predicted;
            return predicted;
        }
    }

    private int safeThreadCap() {
        int cap = memoryClassMb < 96 ? 3 : memoryClassMb < 160 ? 4 : memoryClassMb < 256 ? 6 : 8;
        long heapMb = Math.max(1L, Runtime.getRuntime().maxMemory() / MB);
        if (heapMb < 96L) cap = Math.min(cap, 3);
        else if (heapMb < 160L) cap = Math.min(cap, 4);
        else if (heapMb < 256L) cap = Math.min(cap, 6);
        int cpuCores = Math.max(1, Runtime.getRuntime().availableProcessors());
        cap = Math.min(cap, Math.max(2, cpuCores * 2));
        return Math.max(2, cap);
    }

    public JSONObject snapshot(ThreadPoolExecutor pool) throws JSONException {
        Runtime runtime = Runtime.getRuntime();
        long maxHeap = Math.max(1L, runtime.maxMemory());
        long usedHeap = Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
        JSONObject result = new JSONObject();
        result.put("mode", mode);
        result.put("requests", requests.get());
        result.put("failures", failures.get());
        result.put("rejected", rejected.get());
        result.put("averageLatencyMs", requests.get() == 0 ? 0L : totalLatencyMs.get() / requests.get());
        result.put("learnedLatencyMs", Math.round(latencyEwmaMs));
        result.put("failureRatePercent", Math.round(failureRateEwma * 100.0d));
        result.put("modelSamples", modelSamples);
        result.put("overloadRiskPercent", Math.round(overloadRisk * 100.0d));
        result.put("model", "online-logistic-feedback");
        result.put("heapUsedMb", usedHeap / MB);
        result.put("heapMaxMb", maxHeap / MB);
        result.put("heapPressurePercent", Math.round((double) usedHeap * 100.0d / (double) maxHeap));
        result.put("deviceMemoryClassMb", memoryClassMb);
        result.put("cpuCores", Math.max(1, Runtime.getRuntime().availableProcessors()));
        result.put("workerActive", pool == null ? 0 : pool.getActiveCount());
        result.put("workerMax", pool == null ? 0 : pool.getMaximumPoolSize());
        result.put("queue", pool == null ? 0 : pool.getQueue().size());
        result.put("queueCapacity", QUEUE_CAPACITY);
        result.put("engine", "OHLI AI Performance Engine");
        result.put("offline", true);
        org.json.JSONArray routes = new org.json.JSONArray();
        synchronized (routeLock) {
            for (Map.Entry<String, RouteMetric> entry : routeMetrics.entrySet()) {
                RouteMetric metric = entry.getValue();
                JSONObject row = new JSONObject();
                row.put("route", entry.getKey());
                row.put("requests", metric.count);
                row.put("failures", metric.failures);
                row.put("averageLatencyMs", metric.count == 0 ? 0L : metric.totalMs / metric.count);
                row.put("learnedLatencyMs", Math.round(metric.ewmaMs));
                routes.put(row);
            }
        }
        result.put("routes", routes);
        return result;
    }

    /** Restore learned numeric parameters; migrate the first five-weight model state safely. */
    public void importModelState(String state) {
        if (state == null || state.length() == 0 || state.length() > 512) return;
        String[] parts = state.split("\\|", -1);
        boolean legacyV1 = parts.length == 8 && "ohli-ai-v1".equals(parts[0]);
        boolean currentV2 = parts.length == 9 && "ohli-ai-v2".equals(parts[0]);
        if (!legacyV1 && !currentV2) return;
        synchronized (modelLock) {
            try {
                // Validate the complete record before mutating the live model.
                double[] candidateWeights = modelWeights.clone();
                int weightCount = legacyV1 ? 5 : modelWeights.length;
                for (int i = 0; i < weightCount; i++) {
                    double value = Double.parseDouble(parts[i + 1]);
                    if (Double.isNaN(value) || Double.isInfinite(value) || Math.abs(value) > 12.0d) return;
                    candidateWeights[i] = value;
                }
                int samplesIndex = legacyV1 ? 6 : 7;
                int latencyIndex = legacyV1 ? 7 : 8;
                long samples = Long.parseLong(parts[samplesIndex]);
                double latency = Double.parseDouble(parts[latencyIndex]);
                if (samples < 0L || samples > 1000000000L || Double.isNaN(latency)
                        || Double.isInfinite(latency) || latency < 0.0d || latency > 600000.0d) return;
                System.arraycopy(candidateWeights, 0, modelWeights, 0, modelWeights.length);
                modelSamples = samples;
                lastPersistedSamples = samples;
                latencyEwmaMs = latency;
            } catch (Exception ignored) {
                // Ignore a corrupt/old model state and start from safe built-in priors.
            }
        }
    }

    /** Save at most once per 32 learned samples so model persistence never sits on every request. */
    public boolean shouldPersistModel() {
        synchronized (modelLock) {
            if (modelSamples - lastPersistedSamples < 32L) return false;
            lastPersistedSamples = modelSamples;
            return true;
        }
    }

    public String exportModelState() {
        synchronized (modelLock) {
            StringBuilder out = new StringBuilder("ohli-ai-v2");
            for (double weight : modelWeights) out.append('|').append(String.format(Locale.US, "%.8f", weight));
            out.append('|').append(modelSamples).append('|').append(String.format(Locale.US, "%.4f", latencyEwmaMs));
            return out.toString();
        }
    }

    public boolean shouldCompressJson(int bodyLength) {
        return bodyLength >= 2048 && heapPressureEwma < 0.84d && !"HEAP_GUARD".equals(mode);
    }

    public String statusLine(ThreadPoolExecutor pool) {
        long average = requests.get() == 0 ? 0L : totalLatencyMs.get() / requests.get();
        return String.format(Locale.US, "OHLI AI Performance: %s · risiko %d%% · error %d%% · %d sampel · %d/%d worker · antrean %d/%d · rata-rata %d ms",
                mode, Math.round(overloadRisk * 100.0d), Math.round(failureRateEwma * 100.0d), modelSamples,
                pool == null ? 0 : pool.getActiveCount(), pool == null ? 0 : pool.getMaximumPoolSize(),
                pool == null ? 0 : pool.getQueue().size(), QUEUE_CAPACITY, average);
    }

    private static final class RouteMetric {
        long count;
        long totalMs;
        long failures;
        double ewmaMs;
    }
}
