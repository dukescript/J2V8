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

import com.eclipsesource.v8.JavaCallback;
import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.eclipsesource.v8.V8Value;
import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.net.URL;
import java.util.concurrent.Executor;
import org.netbeans.html.boot.spi.Fn;

final class J2V8Presenter implements Fn.KeepAlive,
Fn.Presenter, Fn.FromJavaScript, Fn.ToJavaScript, Executor, Closeable {
    private final V8 v8;
    private final ThreadLocal<Object> toJava = new ThreadLocal<Object>();

    public J2V8Presenter() {
        v8 = V8.createV8Runtime();
    }

    @Override
    public Fn defineFn(String code, String... names) {
        return defineImpl(code, names, null);
    }

    @Override
    public Fn defineFn(String code, String[] names, boolean[] keepAlive) {
        return defineImpl(code, names, keepAlive);
    }

    private FnImpl defineImpl(String code, String[] names, boolean[] keepAlive) {
        StringBuilder sb = new StringBuilder();
        sb.append("(function() {\n");
        sb.append("  return function(");
        String sep = "";
        if (names != null) {
            for (String n : names) {
                sb.append(sep).append(n);
                sep = ",";
            }
        }
        sb.append(") {\n");
        sb.append(code);
        sb.append("\n  };\n");
        sb.append("})()\n");

        V8Function fn = (V8Function) v8.executeObjectScript(sb.toString());
        return new FnImpl(this, fn, keepAlive);
    }

    @Override
    public void displayPage(URL page, Runnable onPageLoad) {
        v8.executeScript("if (typeof window !== 'undefined') window.location = '" + page + "'");
        if (onPageLoad != null) {
            onPageLoad.run();
        }
    }

    @Override
    public void loadScript(Reader code) throws Exception {
        StringBuilder sb = new StringBuilder();
        char[] arr = new char[4096];
        for (;;) {
            int len = code.read(arr);
            if (len == -1) {
                break;
            }
            sb.append(arr, 0, len);
        }
        v8.executeScript(sb.toString());
    }

    //
    // array conversions
    //
    final V8Array convertArrays(Object arr) {
        V8Array wrapArr = new V8Array(v8);
        if (arr instanceof Object[]) {
            final Object[] typedArray = (Object[])arr;
            int len = typedArray.length;
            for (int i = 0; i < len; i++) {
                pushToArray(wrapArr, typedArray[i]);
            }
        } else if (arr instanceof double[]) {
            final double[] typedArray = (double[])arr;
            int len = typedArray.length;
            for (int i = 0; i < len; i++) {
                pushToArray(wrapArr, typedArray[i]);
            }
        } else if (arr instanceof int[]) {
            final int[] typedArray = (int[])arr;
            int len = typedArray.length;
            for (int i = 0; i < len; i++) {
                pushToArray(wrapArr, typedArray[i]);
            }
        } else if (arr instanceof byte[]) {
            final byte[] typedArray = (byte[])arr;
            int len = typedArray.length;
            for (int i = 0; i < len; i++) {
                pushToArray(wrapArr, typedArray[i]);
            }
        } else if (arr instanceof boolean[]) {
            final boolean[] typedArray = (boolean[])arr;
            int len = typedArray.length;
            for (int i = 0; i < len; i++) {
                pushToArray(wrapArr, typedArray[i]);
            }
        }
        int len = Array.getLength(arr);
        for (int i = 0; i < len; i++) {
            pushToArray(wrapArr, Array.get(arr, i));
        }
        return wrapArr;
    }


    @Override
    public Object toJava(Object obj) {
        if (obj instanceof Weak) {
            obj = ((Weak) obj).get();
        }
        if (obj instanceof V8Array) {
            V8Array arr = (V8Array) obj;
            Object[] plainArr = new Object[arr.length()];
            for (int i = 0; i < plainArr.length; i++) {
                Object elem = arr.get(i);
                plainArr[i] = toJava(elem);
            }
            return plainArr;
        }
        if (obj instanceof V8Function) {
            V8Function fn = (V8Function) obj;
            if (fn.getBoolean("J2V8Presenter")) {
                fn.call(null, new V8Array(v8));
                obj = toJava.get();
                toJava.remove();
                assert obj != null;
            }
        }
        return obj;
    }

    @Override
    public Object toJavaScript(Object toReturn) {
        if (toReturn instanceof Object[]) {
            try {
                return convertArrays((Object[]) toReturn);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        } else {
            return toReturn;
        }
    }

    @Override
    public void execute(final Runnable command) {
        if (Fn.activePresenter() == this) {
            command.run();
            return;
        }

        class Wrap implements Runnable {
            @Override
            public void run() {
                Closeable c = Fn.activate(J2V8Presenter.this);
                try {
                    command.run();
                } finally {
                    try {
                        c.close();
                    } catch (IOException ex) {
                        throw new IllegalStateException(ex);
                    }
                }
            }
        }
        final Runnable wrap = new Wrap();
        wrap.run();
    }

    @Override
    public void close() throws IOException {
        v8.release();
    }

    private void pushToArray(V8Array all, final Object value) {
        if (value instanceof String) {
            all.push((String)value);
        } else if (value instanceof Number) {
            if (value instanceof Integer || value instanceof Byte || value instanceof Short) {
                all.push(((Number)value).intValue());
            } else if (value instanceof Long) {
                final Long longValue = (Long) value;
                if (Integer.MIN_VALUE <= longValue && longValue <= Integer.MAX_VALUE) {
                    all.push(longValue.intValue());
                } else {
                    all.push(longValue.doubleValue());
                }
            } else {
                all.push(((Number) value).doubleValue());
            }
        } else if (value instanceof Boolean) {
            all.push((Boolean)value);
        } else if (value == null) {
            all.pushNull();
        } else if (value instanceof Character) {
            all.push(value.toString());
        } else if (value instanceof V8Value) {
            all.push((V8Value) value);
        } else if (isArray(value)) {
            all.push(convertArrays(value));
        } else {
            V8Function wrapper = new V8Function(v8, new JavaCallback() {
                @Override
                public Object invoke(V8Object receiver, V8Array parameters) {
                    toJava.set(value);
                    return true;
                };
            });
            wrapper.add("J2V8Presenter", true);
            all.push(wrapper);
        }
    }

    private boolean isArray(Object value) {
        return value instanceof Object[] ||
            value instanceof byte[] ||
            value instanceof char[] ||
            value instanceof short[] ||
            value instanceof int[] ||
            value instanceof long[] ||
            value instanceof float[] ||
            value instanceof double[];
    }

    private class FnImpl extends Fn {

        private final V8Function fn;
        private final boolean[] keepAlive;

        public FnImpl(Fn.Presenter presenter, V8Function fn, boolean[] keepAlive) {
            super(presenter);
            this.fn = fn;
            this.keepAlive = keepAlive;
        }

        @Override
        public Object invoke(Object thiz, Object... args) throws Exception {
            return invokeImpl(thiz, true, args);
        }

        final Object invokeImpl(Object thiz, boolean arrayChecks, Object... args) throws Exception {
            final J2V8Presenter presenter = (J2V8Presenter) presenter();
            V8Array all = new V8Array(v8);
            pushToArray(all, thiz);
            V8Object jsThis = all.getObject(0);
            all = new V8Array(v8);
            for (int i = 0; i < args.length; i++) {
                presenter.pushToArray(all, args[i]);
//                Object conv = args[i];
//                if (arrayChecks) {
//                    if (args[i] instanceof Object[]) {
//                        Object[] arr = (Object[]) args[i];
//                        conv = ((J2V8Presenter) presenter()).convertArrays(arr);
//                    }
//                    if (conv != null && keepAlive != null
//                            && !keepAlive[i] && !isJSReady(conv)
//                            && !conv.getClass().getSimpleName().equals("$JsCallbacks$") // NOI18N
//                            ) {
//                        conv = new Weak(conv);
//                    }
//                    if (conv instanceof Character) {
//                        conv = (int) (Character) conv;
//                    }
//                }
//                all.pu
              //  all.add(conv);
            }
            Object ret = fn.call(jsThis, all);
            if (ret instanceof Weak) {
                ret = ((Weak) ret).get();
            }
            return presenter.toJava(ret);
        }
    }

    private static final class Weak extends WeakReference<Object> {

        public Weak(Object referent) {
            super(referent);
        }
    }
}
