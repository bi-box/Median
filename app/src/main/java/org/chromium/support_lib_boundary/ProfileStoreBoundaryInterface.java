/* Copyright 2023 The Chromium Authors. BSD-style license. */
package org.chromium.support_lib_boundary;

import java.lang.reflect.InvocationHandler;

/** Focused copy of the public Chromium support-library profile-store protocol. */
public interface ProfileStoreBoundaryInterface {
    InvocationHandler getOrCreateProfile(String name);
}
