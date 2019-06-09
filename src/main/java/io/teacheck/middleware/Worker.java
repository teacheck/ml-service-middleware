package io.teacheck.middleware;

import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.codec.BodyCodec;

import java.util.concurrent.Callable;

public class Worker implements Callable<Single<JsonArray>> {

    private WebClient client;
    private JsonObject data;

    public Worker(WebClient client, JsonObject data) {
        this.client = client;
        this.data = data;
    }

    @Override
    public Single<JsonArray> call() throws Exception {
        Single<HttpResponse<JsonArray>> responseSingle = client.post("/predict")
                .putHeader("Content-Type", "application/json")
                .as(BodyCodec.jsonArray())
                .rxSendJsonObject(data);
        return responseSingle.map(HttpResponse::body);
    }
}
