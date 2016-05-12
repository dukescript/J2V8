
package com.eclipsesource.v8.presenter;

import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import net.java.html.boot.BrowserBuilder;
import org.junit.Test;
import org.netbeans.html.boot.spi.Fn;
import org.netbeans.html.json.tck.JavaScriptTCK;
import org.netbeans.html.json.tck.KOTest;

public class J2V8PresenterTest extends JavaScriptTCK {    
    public J2V8PresenterTest() {
    }

    @Test
    public void compatibilityTests() throws Exception {
        List<Object> res = new ArrayList<Object>();
        Class<? extends Annotation> test = 
            J2V8PresenterTest.class.getClassLoader().loadClass(KOTest.class.getName()).
            asSubclass(Annotation.class);

        J2V8Presenter p = new J2V8Presenter();
        Closeable close = Fn.activate(p);
        Class[] arr = testClasses();
        for (Class c : arr) {
            for (Method m : c.getMethods()) {
                if (m.getAnnotation(test) != null) {
                    Object instance = c.newInstance();
                    m.invoke(instance);
                }
            }
        }
        close.close();
    }
}
