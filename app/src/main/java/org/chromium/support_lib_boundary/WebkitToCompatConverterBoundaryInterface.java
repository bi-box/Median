/* Copyright 2018 The Chromium Authors. BSD-style license. */
package org.chromium.support_lib_boundary;

import android.webkit.WebSettings;

import java.lang.reflect.InvocationHandler;

/** Focused boundary surface for converting framework WebSettings to the provider adapter. */
public interface WebkitToCompatConverterBoundaryInterface {
    InvocationHandler convertSettings(WebSettings webSettings);
}
