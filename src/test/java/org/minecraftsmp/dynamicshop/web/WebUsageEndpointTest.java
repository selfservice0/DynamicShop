package org.minecraftsmp.dynamicshop.web;

import io.javalin.Javalin;
import org.junit.Test;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class WebUsageEndpointTest {
    @Test public void privacySignalsDisableCountsAndOnlyEventNamesAreAccepted() throws Exception {
        var on = new AtomicBoolean(true);
        var metrics = new WebUsageMetrics(on::get);
        try (var app = Javalin.create().post("/event", ctx -> WebServer.handleUsageEvent(ctx, metrics)).start("127.0.0.1", 0)) {
            var client = HttpClient.newHttpClient();
            var uri = URI.create("http://127.0.0.1:" + app.port() + "/event");
            for (String signal : new String[]{"DNT", "Sec-GPC"}) {
                var request = HttpRequest.newBuilder(uri).header(signal,"1").header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"event\":\"page_view\"}")).build();
                assertEquals(204, client.send(request,HttpResponse.BodyHandlers.discarding()).statusCode());
            }
            assertEquals(0, metrics.drain("page_view"));
            assertEquals(400, post(client,uri,"{\"event\":\"search\",\"query\":\"private\"}"));
            assertEquals(400, post(client,uri,"{\"event\":\"unknown\"}"));
            assertEquals(413, post(client,uri," ".repeat(129)));
            assertEquals(204, post(client,uri,"{\"event\":\"page_view\"}"));
            assertEquals(1, metrics.drain("page_view"));
            on.set(false);
            assertEquals(204, post(client,uri,"{\"event\":\"page_view\"}"));
            on.set(true);
            assertEquals(0, metrics.drain("page_view"));
        }
    }
    private int post(HttpClient client, URI uri, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
