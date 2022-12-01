package dev.azn9.ytdlapi;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main extends AbstractVerticle {

    public static void main(String[] args) {
        System.out.println("V1");
        Vertx.vertx().deployVerticle(new Main());
    }

    @Override
    public void start(Promise fut) {
        Router router = Router.router(this.vertx);

        router.route("/").handler(BodyHandler.create());
        router.post("/").blockingHandler(routingContext -> {
            try {
                this.handle(routingContext);
            } catch (IOException | ExecutionException | InterruptedException e) {
                e.printStackTrace();
                routingContext.response().setStatusCode(500).end();
            }
        });

        this.vertx.createHttpServer()
                .requestHandler(router)
                .listen(this.config().getInteger("http.port", 1234),
                        result -> {
                            if (result.succeeded()) {
                                fut.complete();
                            } else {
                                fut.fail(result.cause());
                            }
                        }
                );
    }

    private void handle(RoutingContext routingContext) throws IOException, ExecutionException, InterruptedException {
        String query = routingContext.getBodyAsJson().getString("query");
        boolean isSearch = false;

        System.out.println("Received request : " + query);

        Matcher matcher = Pattern.compile("^((?:https?:)?\\/\\/)?((?:www|m)\\.)?(youtube\\.com|youtu.be)(\\/(?:[\\w\\-]+\\?v=|embed\\/|v\\/)?)(?<code>[\\w\\-]+)(\\S+)?$").matcher(query);
        if (matcher.matches()) {
            query = matcher.group("code");
        } else {
            query = query.replace('|', ';');
            query = query.replaceAll(";", "");
            query = query.replaceAll(">", "");

            isSearch = true;

            System.out.println("Search : " + query);
        }

        if (query == null || query.isBlank() || query.isEmpty()) {
            System.out.println("query null");

            routingContext.response()
                    .setStatusCode(500)
                    .end(query);
            return;
        }

        boolean finalIsSearch = isSearch;
        String finalQuery = query;

        ProcessBuilder processBuilder = new ProcessBuilder("/data/youtube-dl", "--skip-download", "--get-id", finalIsSearch ? "ytsearch:" + finalQuery : "https://youtu.be/" + finalQuery);
        String code = "";

        try {
            Process process = processBuilder.start();
            process.waitFor();
            code = new String(process.getInputStream().readAllBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Code : " + code);

        routingContext.response().setStatusCode(200).end(code);
    }

}
