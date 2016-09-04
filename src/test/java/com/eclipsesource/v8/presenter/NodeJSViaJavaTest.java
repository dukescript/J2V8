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

import com.eclipsesource.v8.NodeJS;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.java.html.lib.Objs;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;
import org.netbeans.html.boot.spi.Fn;
import net.java.html.lib.node.*;
import static net.java.html.lib.Exports.eval;
import net.java.html.lib.Function;
import net.java.html.lib.node.http.Server;
import net.java.html.lib.node.http.ServerRequest;
import net.java.html.lib.node.http.ServerResponse;

public class NodeJSViaJavaTest {
    private J2V8Presenter presenter;
    private NodeJS node;

    @Before
    public void createNode() {
        node = NodeJS.createNodeJS();
        presenter = new J2V8Presenter(node.getRuntime());
    }

    @After
    public void destroyNode() {
        //presenter.close();
    }

    @Test
    public void runServerAndConnetToIt() throws Exception {
        Closeable c = Fn.activate(presenter);
        try {
            runServerAndConnetToItImpl();
        } finally {
            c.close();
        }
    }

    private void runServerAndConnetToItImpl() throws Exception {
        exportRequireAsGlobal();

        final int port = findEmptyPort();

        Objs http = Objs.$as(Exports.require.$apply("http"));
        assertNotNull("Script file found", http);

        Function createServer = Function.$as(http.$get("createServer"));
        assertNotNull("Create method found", createServer);

        Object serverRaw = createServer.apply(http, new Function.A2<ServerRequest, ServerResponse, Void>() {
            @Override
            public Void call(ServerRequest request, ServerResponse response) {
                response.end("Connected: " + request.url.get());
                return null;
            }

            @Override
            public Void call(ServerRequest request, ServerResponse response, Object p3) {
                return call(request, response);
            }

            @Override
            public Void call(ServerRequest request, ServerResponse response, Object p3, Object p4) {
                return call(request, response);
            }

            @Override
            public Void call(ServerRequest request, ServerResponse response, Object p3, Object p4, Object p5) {
                return call(request, response);
            }
        });
        Server server = Server.$as(serverRaw);
        server.listen(port, Function.newFunction(new Function.A0<Void>() {
            @Override
            public Void call() {
                System.err.println("Server listening on: http://localhost:" + port);
                return null;
            }

            @Override
            public Void call(Object p1) {
                return call();
            }

            @Override
            public Void call(Object p1, Object p2) {
                return call();
            }

            @Override
            public Void call(Object p1, Object p2, Object p3) {
                return call();
            }

            @Override
            public Void call(Object p1, Object p2, Object p3, Object p4) {
                return call();
            }

            @Override
            public Void call(Object p1, Object p2, Object p3, Object p4, Object p5) {
                return call();
            }
        }));

        Future<String> done = Executors.newSingleThreadExecutor().submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                URL u = new URL("http://127.0.0.1:" + port + "/hello");
                final URLConnection conn = u.openConnection();

                InputStreamReader r = new InputStreamReader(conn.getInputStream());
                BufferedReader br = new BufferedReader(r);
                String line = br.readLine();
                return line;
            }
        });

        while (!done.isDone()) {
            process(node, done);
        }

        assertEquals("Connected: /hello", done.get());
    }

    private void exportRequireAsGlobal() throws IOException {
        final Objs global = Objs.$as(eval("global"));
        global.$set("__run", new net.java.html.lib.Function.A5() {
            @Override
            public Object call(Object require, Object p2, Object p3, Object p4, Object p5) {
                global.$set("require", require);
                return null;
            }
        });

        File f = createScriptFile("global.__run(require, exports, module, __filename, __dirname);");
        node.require(f);
    }

    private File createScriptFile(String code) throws IOException {
        File serverScript = File.createTempFile("temp", ".js");
        FileWriter w = new FileWriter(serverScript);
        w.write(code);
        w.close();
        serverScript.deleteOnExit();
        return serverScript;
    }

    private int findEmptyPort() throws IOException {
        final ServerSocket ss = new ServerSocket(0);
        final int port = ss.getLocalPort();
        ss.close();
        return port;
    }

    private void process(NodeJS node, Future<?> await) {
        while (node.isRunning()) {
            if (await != null && await.isDone()) {
                break;
            }
            node.handleMessage();
        }
    }
}
