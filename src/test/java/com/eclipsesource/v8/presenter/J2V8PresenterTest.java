/*******************************************************************************
 * Copyright (c) 2016 EclipseSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Dukehoff GmbH - Presenter implementation
 ******************************************************************************/
package com.eclipsesource.v8.presenter;

import org.junit.runner.RunWith;
import org.netbeans.html.json.tck.JavaScriptTCK;

@RunWith(PresenterRunner.class)
public class J2V8PresenterTest extends JavaScriptTCK {
    public J2V8PresenterTest() {
    }

    public static Class[] testClasses() {
        return JavaScriptTCK.testClasses();
    }
}
