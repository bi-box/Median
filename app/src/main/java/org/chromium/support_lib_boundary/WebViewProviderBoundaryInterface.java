/* Copyright 2018 The Chromium Authors. BSD-style license. */
package org.chromium.support_lib_boundary;

import java.lang.reflect.InvocationHandler;

public interface WebViewProviderBoundaryInterface {
    InvocationHandler addDocumentStartJavaScript(String script, String[] allowedOriginRules);
}
