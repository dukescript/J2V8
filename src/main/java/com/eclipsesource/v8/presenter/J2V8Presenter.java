package com.eclipsesource.v8.presenter;

import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Object;
import com.eclipsesource.v8.V8Value;
import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.html.boot.spi.Fn;

public class J2V8Presenter implements Fn.KeepAlive,
Fn.Presenter, Fn.FromJavaScript, Fn.ToJavaScript, Executor {

    private static final Logger LOG = Logger.getLogger(J2V8Presenter.class.getName());
    private final V8 v8;

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

        V8Object fn = v8.executeObjectScript(sb.toString());
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
    final Object convertArrays(Object[] arr) throws Exception {
        V8Array wrapArr = new V8Array(v8);
        for (int i = 0; i < arr.length; i++) {
            Object obj = arr[i];
            if (obj instanceof Object[]) {
                obj = convertArrays((Object[]) arr[i]);
            }
            if (obj instanceof V8Value) {
                wrapArr.push((V8Value)obj);
            } else if (obj instanceof String) {
                wrapArr.push((String)obj);
            } else if (obj instanceof Number) {
                Number n = (Number) obj;
                wrapArr.push(n.doubleValue());
            } else if (obj instanceof Boolean) {
                wrapArr.push((Boolean)obj);
            } else {
                throw new IllegalStateException("Cannot convert: " + obj);
            }
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

    private class FnImpl extends Fn {

        private final V8Object fn;
        private final boolean[] keepAlive;

        public FnImpl(Fn.Presenter presenter, V8Object fn, boolean[] keepAlive) {
            super(presenter);
            this.fn = fn;
            this.keepAlive = keepAlive;
        }

        @Override
        public Object invoke(Object thiz, Object... args) throws Exception {
            return invokeImpl(thiz, true, args);
        }

        final Object invokeImpl(Object thiz, boolean arrayChecks, Object... args) throws Exception {
            V8Array all = new V8Array(v8);
        //    all.push(thiz == null ? fn : thiz);
            for (int i = 0; i < args.length; i++) {
                Object conv = args[i];
                if (arrayChecks) {
                    if (args[i] instanceof Object[]) {
                        Object[] arr = (Object[]) args[i];
                        conv = ((J2V8Presenter) presenter()).convertArrays(arr);
                    }
                    if (conv != null && keepAlive != null
                            && !keepAlive[i] && !isJSReady(conv)
                            && !conv.getClass().getSimpleName().equals("$JsCallbacks$") // NOI18N
                            ) {
                        conv = new Weak(conv);
                    }
                    if (conv instanceof Character) {
                        conv = (int) (Character) conv;
                    }
                }
              //  all.add(conv);
            }
            Object ret = fn.executeObjectFunction("call", all);
            if (ret instanceof Weak) {
                ret = ((Weak) ret).get();
            }
            if (ret == fn) {
                return null;
            }
            if (!arrayChecks) {
                return ret;
            }
            return ((J2V8Presenter) presenter()).toJava(ret);
        }
    }

    private static boolean isJSReady(Object obj) {
        if (obj == null) {
            return true;
        }
        if (obj instanceof String) {
            return true;
        }
        if (obj instanceof Number) {
            return true;
        }
        final String cn = obj.getClass().getName();
        if (cn.startsWith("jdk.nashorn") || ( // NOI18N
                cn.contains(".mozilla.") && cn.contains(".Native") // NOI18N
                )) {
            return true;
        }
        if (obj instanceof Character) {
            return true;
        }
        return false;
    }

    private static final class Weak extends WeakReference<Object> {

        public Weak(Object referent) {
            super(referent);
        }
    }
}
