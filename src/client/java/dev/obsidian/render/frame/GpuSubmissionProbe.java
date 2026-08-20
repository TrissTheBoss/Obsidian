package dev.obsidian.render.frame;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.OptionalLong;

/**
 * One-shot, non-visual GPU command submission used to prove that Obsidian can
 * encode, submit, and asynchronously observe GPU work through Minecraft 26.2's
 * active Vulkan device.
 *
 * <p>It deliberately does not run every frame. Adding a command submission per
 * timestamp boundary would distort the frame pacing that Obsidian is intended
 * to improve.</p>
 */
public final class GpuSubmissionProbe implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/Phase1GpuProbe");

    public enum State {
        ARMED,
        SUBMITTED,
        COMPLETED,
        FAILED,
        CLOSED
    }

    private final GpuDevice device;
    private GpuQueryPool queryPool;
    private State state = State.ARMED;
    private long submittedFrame = -1L;
    private long completedFrame = -1L;
    private long firstTimestamp;
    private long secondTimestamp;

    public GpuSubmissionProbe(GpuDevice device) {
        this.device = device;
    }

    public void submit(long frameIndex) {
        RenderSystem.assertOnRenderThread();
        if (state != State.ARMED) {
            return;
        }

        GpuQueryPool pool = null;
        try {
            pool = device.createTimestampQueryPool(2);
            CommandEncoder encoder = device.createCommandEncoder();
            encoder.writeTimestamp(pool, 0);
            encoder.writeTimestamp(pool, 1);
            encoder.submit();

            queryPool = pool;
            submittedFrame = frameIndex;
            state = State.SUBMITTED;
            LOG.log(System.Logger.Level.INFO,
                    "Phase 1 GPU probe submitted on frame {0}; waiting asynchronously for timestamp results.",
                    frameIndex);
        } catch (RuntimeException e) {
            if (pool != null) {
                pool.close();
            }
            state = State.FAILED;
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 1 GPU submission probe failed; Obsidian will keep running so the failure can be diagnosed.",
                    e);
        }
    }

    public void poll(long frameIndex) {
        RenderSystem.assertOnRenderThread();
        if (state != State.SUBMITTED || queryPool == null) {
            return;
        }

        try {
            OptionalLong first = queryPool.getValue(0);
            OptionalLong second = queryPool.getValue(1);
            if (first.isEmpty() || second.isEmpty()) {
                return;
            }

            firstTimestamp = first.getAsLong();
            secondTimestamp = second.getAsLong();
            completedFrame = frameIndex;
            state = State.COMPLETED;
            queryPool.close();
            queryPool = null;

            LOG.log(System.Logger.Level.INFO,
                    "Phase 1 GPU probe completed on frame {0} after {1} frame(s): timestamp0={2}, timestamp1={3}, deltaTicks={4}.",
                    completedFrame,
                    completedFrame - submittedFrame,
                    firstTimestamp,
                    secondTimestamp,
                    secondTimestamp - firstTimestamp);
        } catch (RuntimeException e) {
            state = State.FAILED;
            queryPool.close();
            queryPool = null;
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 1 GPU submission probe could not read timestamp results; continuing without the probe.",
                    e);
        }
    }

    public State state() {
        return state;
    }

    public long submittedFrame() {
        return submittedFrame;
    }

    public long completedFrame() {
        return completedFrame;
    }

    public long timestampDeltaTicks() {
        return secondTimestamp - firstTimestamp;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (state == State.CLOSED) {
            return;
        }
        if (queryPool != null) {
            queryPool.close();
            queryPool = null;
        }
        state = State.CLOSED;
    }
}
