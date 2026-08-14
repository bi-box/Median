/* Copyright 2018 The Chromium Authors. BSD-style license. */
package org.chromium.support_lib_boundary;

/** Focused boundary surface for WebView's native algorithmic darkening switch. */
public interface WebSettingsBoundaryInterface {
    void setAlgorithmicDarkeningAllowed(boolean allow);
    boolean isAlgorithmicDarkeningAllowed();
}
