package com.hedera.demo.auction.node.app.api;

import com.hedera.demo.auction.node.app.CreateAuctionAccount;
import com.hedera.hashgraph.sdk.AccountId;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class PostAuctionAccountHandler implements Handler<RoutingContext> {
    public PostAuctionAccountHandler() {
    }

    @Override
    public void handle(RoutingContext routingContext) {
        var body = routingContext.getBodyAsJson();

        if (body == null) {
            routingContext.fail(400);
            return;
        }

        var data = body.mapTo(RequestCreateAuctionAccount.class);

        try {
            AccountId auctionAccount = CreateAuctionAccount.create(data.initialBalance, data.threshold, data.keys);

            JsonObject response = new JsonObject();
            response.put("accountId", auctionAccount.toString());

            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end(Json.encodeToBuffer(response));
        } catch (Exception e) {
            routingContext.fail(400, e);
            return;
        }
    }
}