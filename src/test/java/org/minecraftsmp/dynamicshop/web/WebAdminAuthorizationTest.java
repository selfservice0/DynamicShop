package org.minecraftsmp.dynamicshop.web;

import io.javalin.Javalin;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;

public class WebAdminAuthorizationTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void unauthorizedRequestsNeverReachMutationHandlers() throws Exception {
        var plugin = PluginTestFixture.plugin(temp.getRoot());
        var web = new WebServer(plugin);
        var writes = new AtomicInteger();
        try (var app =
                Javalin.create()
                        .before("/api/admin/*", web::requireAdmin)
                        .post(
                                "/api/admin/probe",
                                ctx -> {
                                    writes.incrementAndGet();
                                    ctx.result("saved");
                                })
                        .start("127.0.0.1", 0)) {
            var uri = URI.create("http://127.0.0.1:" + app.port() + "/api/admin/probe");
            var client = HttpClient.newHttpClient();
            assertEquals(
                    401,
                    client.send(
                                    HttpRequest.newBuilder(uri)
                                            .POST(HttpRequest.BodyPublishers.noBody())
                                            .build(),
                                    HttpResponse.BodyHandlers.ofString())
                            .statusCode());
            assertEquals(0, writes.get());
            web.getUserManager().register("Admin", "testing123");
            String session = web.getUserManager().login("Admin", "testing123");
            var request =
                    HttpRequest.newBuilder(uri)
                            .header("X-Session-Token", session)
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build();
            assertEquals(
                    200, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(1, writes.get());
            web.getUserManager().logout(session);
            assertEquals(
                    401, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(1, writes.get());
        }
    }
}
