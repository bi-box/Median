/* Copyright 2023 The Chromium Authors. BSD-style license. */
package org.chromium.support_lib_boundary;

/** Focused copy of the public Chromium connection-only prewarming protocol. */
public interface ProfileBoundaryInterface {
    void preconnect(String url);
    void enqueuePreconnect(String url);
}
