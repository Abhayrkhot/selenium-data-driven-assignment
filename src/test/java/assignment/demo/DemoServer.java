package assignment.demo;

import assignment.data.WorkbookData;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local illustrative website. Its displayed business values come from the Excel workbook. */
public final class DemoServer implements AutoCloseable {
    private final HttpServer server;
    private final Map<String, Dataset> datasets;
    private final Map<String, String> data;
    private final ExecutorService executor;

    private DemoServer(HttpServer server, Map<String, Dataset> datasets, Map<String, String> data,
                       ExecutorService executor) {
        this.server = server;
        this.datasets = datasets;
        this.data = data;
        this.executor = executor;
    }

    public static DemoServer start(WorkbookData workbook) throws IOException {
        var datasets = new LinkedHashMap<String, Dataset>();
        for (var row : workbook.rows("DemoData")) {
            var dataset = new Dataset(row.get("dataset_id"), row.get("title"), row.get("description"),
                row.get("download_filename"), Boolean.parseBoolean(row.get("download_allowed")));
            datasets.put(dataset.id(), dataset);
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        ExecutorService executor = Executors.newCachedThreadPool();
        var demo = new DemoServer(server, Map.copyOf(datasets), workbook.dataValues(), executor);
        server.createContext("/", demo::handle);
        server.setExecutor(executor);
        server.start();
        return demo;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                html(exchange, catalogPage());
            } else if (path.startsWith("/dataset/")) {
                Dataset dataset = datasets.get(path.substring("/dataset/".length()));
                if (dataset == null) notFound(exchange); else html(exchange, detailsPage(dataset));
            } else if (path.startsWith("/download/")) {
                download(exchange, path.substring("/download/".length()));
            } else {
                notFound(exchange);
            }
        } finally {
            exchange.close();
        }
    }

    private String catalogPage() {
        StringBuilder cases = new StringBuilder();
        for (Dataset dataset : datasets.values()) {
            cases.append("case '").append(js(dataset.id())).append("':")
                .append("title='").append(js(dataset.title())).append("';")
                .append("description='").append(js(dataset.description())).append("';break;");
        }
        return """
            <!doctype html><html lang="en"><head><meta charset="utf-8">
            <title>%s</title><style>%s</style></head><body><main>
            <h1 data-testid="catalog-heading">%s</h1>
            <p>Illustrative local catalog for framework verification.</p>
            <label for="search">Dataset search</label>
            <div><input id="search" data-testid="search-input" autocomplete="off">
            <button data-testid="search-submit" type="button">Search</button></div>
            <section class="result"><h2 data-testid="result-title"></h2>
            <p data-testid="result-description"></p></section>
            </main><script>
            document.querySelector('[data-testid="search-submit"]').addEventListener('click',()=>{
              const query=document.querySelector('[data-testid="search-input"]').value;
              let title='No matching dataset',description='';
              switch(query){%s}
              document.querySelector('[data-testid="result-title"]').textContent=title;
              document.querySelector('[data-testid="result-description"]').textContent=description;
            });
            </script></body></html>
            """.formatted(html(data.get("demo_heading")), styles(), html(data.get("demo_heading")), cases);
    }

    private String detailsPage(Dataset dataset) {
        String click = dataset.downloadAllowed()
            ? "window.location.href='/download/" + js(dataset.filename()) + "'"
            : "document.querySelector('[data-testid=\"download-status\"]').textContent='"
                + js(data.get("demo_denial")) + "'";
        return """
            <!doctype html><html lang="en"><head><meta charset="utf-8">
            <title>%s</title><style>%s</style></head><body><main>
            <a href="/">Back to catalog</a>
            <h1 data-testid="details-title">%s</h1>
            <p data-testid="details-description">%s</p>
            <button data-testid="download-button" type="button">Download dataset</button>
            <p class="status" data-testid="download-status" aria-live="polite"></p>
            </main><script>document.querySelector('[data-testid="download-button"]').addEventListener('click',()=>{%s});</script>
            </body></html>
            """.formatted(html(dataset.title()), styles(), html(dataset.title()), html(dataset.description()), click);
    }

    private void download(HttpExchange exchange, String filename) throws IOException {
        Dataset match = datasets.values().stream().filter(dataset -> dataset.filename().equals(filename)).findFirst().orElse(null);
        if (match == null || !match.downloadAllowed()) {
            byte[] denied = data.get("demo_denial").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(403, denied.length);
            exchange.getResponseBody().write(denied);
            return;
        }
        byte[] body = "record_id,value\n1,example\n".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=utf-8");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + match.filename() + "\"");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
    }

    private static void html(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void notFound(HttpExchange exchange) throws IOException {
        byte[] bytes = "Not found".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(404, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static String styles() {
        return "body{font:16px system-ui;background:#f4f7fb;color:#172033;margin:0}main{max-width:760px;margin:64px auto;background:white;padding:36px;border-radius:14px;box-shadow:0 8px 30px #1831531c}input{padding:10px;width:320px}button{padding:10px 16px;margin-left:8px;background:#235789;color:white;border:0;border-radius:6px}.result,.status{margin-top:24px}";
    }

    private static String html(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String js(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\r", "\\r")
            .replace("\n", "\\n").replace("</", "<\\/");
    }

    @Override public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private record Dataset(String id, String title, String description, String filename, boolean downloadAllowed) {}
}
