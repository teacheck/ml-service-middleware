package io.teacheck.middleware;

import io.reactivex.Single;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.handler.BodyHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class Server extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(Server.class);

    private Router router;
    private WebClient webClient;
    private ExecutorService service;
    private CompletionService<Single<JsonArray>> completionService;

    @Override
    public void start(Future<Void> startFuture) {
        buildRoute();
        setupWebClient();
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(Constants.SERVER_PORT, ar -> {
                    if (ar.succeeded()) {
                        startFuture.complete();
                    } else {
                        startFuture.fail(ar.cause());
                    }
                });
    }

    private void setupWebClient() {
        WebClientOptions options = new WebClientOptions()
                .setDefaultPort(Constants.ML_SERVICE_PORT)
                .setDefaultHost(Constants.ML_SERVICE_HOST);
        webClient = WebClient.create(vertx, options);
    }

    private void setupCompletionService() {
        service = Executors.newFixedThreadPool(Constants.NUM_THREADS);
        completionService = new ExecutorCompletionService<>(service);
    }

    private void buildRoute() {
        router = Router.router(vertx);
        router.route("/*").handler(BodyHandler.create());
        router.post("/predict").handler(this::predict);
    }

    private void predict(RoutingContext ctx) {
        setupCompletionService();
        JsonArray jsonArray = ctx.getBodyAsJsonArray();
        for (int i = 0; i < Constants.NUM_PREDICTIONS; i++) {
            JsonObject data = jsonArray.getJsonObject(i);
            vertx.executeBlocking(future -> {
                        completionService.submit(new Worker(webClient, data));
                        future.complete();
                    },
                    asyncResult -> {
                        if (asyncResult.succeeded()) {
                            logger.info("task submited");
                        } else {
                            logger.error("Error while scheduling task");
                        }
                    });
        }
        vertx.executeBlocking(future -> {
                    List<Single<JsonArray>> singles = new ArrayList<>();
                    for (int i = 0; i < Constants.NUM_PREDICTIONS; i++) {
                        try {
                            singles.add(completionService.take().get());
                        } catch (InterruptedException | ExecutionException e) {
                            logger.error("InterruptedException | ExecutionException: " + e.getMessage());
                        }
                    }
                    future.complete(singles);
                },
                asyncResult -> {
                    shutdownCompletionService();
                    if (asyncResult.succeeded()) {
                        List<Single<JsonArray>> singles = (List<Single<JsonArray>>) asyncResult.result();
                        Single.zip(singles.get(0), singles.get(1), singles.get(2), singles.get(3), singles.get(4),
                                (data1, data2, data3, data4, data5) -> new JsonObject()
                                        .put("asignatura-1", data1)
                                        .put("asignatura-2", data2)
                                        .put("asignatura-3", data3)
                                        .put("asignatura-4", data4)
                                        .put("asignatura-5", data5))
                                .subscribe(entries -> ctx.response()
                                                .putHeader("Content-Type", "application/json")
                                                .end(entries.encodePrettily()),
                                        err -> {
                                            logger.error(err.getMessage());
                                            ctx.response().setStatusCode(500).end(err.getMessage());
                                        });
                    } else {
                        logger.error("Failed to retrieve tasks result.");
                        ctx.fail(asyncResult.cause());
                    }
                });
    }

    private void shutdownCompletionService() {
        logger.info("Shutting down ExecutorService.");
        service.shutdown();
        try {
            service.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.error("InterruptedException: " + e.getMessage());
        }
        logger.info("ExecutorService Terminated");
    }
}
