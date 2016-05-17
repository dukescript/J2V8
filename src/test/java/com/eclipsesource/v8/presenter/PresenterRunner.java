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

import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.netbeans.html.boot.spi.Fn;
import org.netbeans.html.json.tck.KOTest;

public class PresenterRunner extends Runner {
    private final Description suite;
    private final J2V8Presenter presenter;

    public PresenterRunner(Class<?> clazz) throws Exception {
        suite = Description.createSuiteDescription(clazz);
        Class<? extends Annotation> test
            = clazz.getClassLoader().loadClass(KOTest.class.getName()).
            asSubclass(Annotation.class);

        Class[] arr = (Class[]) clazz.getMethod("testClasses").invoke(null);
        for (Class c : arr) {
            for (Method m : c.getMethods()) {
                if (m.getAnnotation(test) != null) {
                    Description ch = Description.createTestDescription(c, m.getName());
                    suite.addChild(ch);
                }
            }
        }
        presenter = new J2V8Presenter();
    }

    @Override
    public Description getDescription() {
        return suite;
    }

    @Override
    public void run(RunNotifier rn) {
        for (Description description : suite.getChildren()) {
            try {
                rn.fireTestStarted(description);
                Class<?> clazz = description.getTestClass();
                Object instance = clazz.newInstance();
                Method m = clazz.getMethod(description.getMethodName());
                Closeable close = Fn.activate(presenter);
                try {
                    m.invoke(instance);
                } finally {
                    close.close();
                }
                rn.fireTestFinished(description);
            } catch (Throwable ex) {
                if (ex instanceof InvocationTargetException) {
                    ex = ((InvocationTargetException)ex).getCause();
                }
                rn.fireTestFailure(new Failure(description, ex));
            }
        }
        try {
            presenter.close();
        } catch (Exception ex) {
            rn.fireTestFailure(new Failure(suite, ex));
        }
    }

}
