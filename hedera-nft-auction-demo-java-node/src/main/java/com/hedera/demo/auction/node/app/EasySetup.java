package com.hedera.demo.auction.node.app;

import com.google.errorprone.annotations.Var;
import com.hedera.demo.auction.node.app.repository.AuctionsRepository;
import com.hedera.demo.auction.node.app.repository.BidsRepository;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.TokenId;
import io.github.cdimascio.dotenv.Dotenv;
import io.vertx.core.json.JsonObject;
import lombok.extern.log4j.Log4j2;

import java.io.FileWriter;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;

@Log4j2
public class EasySetup {

    private final static Dotenv env = Dotenv.configure().ignoreIfMissing().load();
    final static SqlConnectionManager connectionManager = new SqlConnectionManager(env);
    final static AuctionsRepository auctionsRepository = new AuctionsRepository(connectionManager);
    final static BidsRepository bidsRepository = new BidsRepository(connectionManager);
    static String topicId = Optional.ofNullable(env.get("VUE_APP_TOPIC_ID")).orElse("");

    private EasySetup() {
    }

    public static void main(String[] args) throws Exception {
        Client client = HederaClient.getClient();

        @Var String symbol = "TT";
        @Var boolean clean = true;
        @Var String name = "Test Token";

        for (String arg : args) {
            if (arg.startsWith("--symbol")) {
                symbol = arg.replace("--symbol=","");
            }
            if (arg.startsWith("--no-clean")) {
                clean = false;
            }
            if (arg.startsWith("--name")) {
                name = arg.replace("--name=","");
            }
        }
        if (clean) {
            log.info("Deleting existing auctions and bids and creating new topic");
            bidsRepository.deleteAllBids();
            auctionsRepository.deleteAllAuctions();
            topicId = CreateTopic.create().toString();
        }

        TokenId tokenId = CreateToken.create(name, symbol, 1L, 0);
        String key = client.getOperatorPublicKey().toString();
        AccountId auctionAccount = CreateAuctionAccount.create(100, key);
        CreateTokenTransfer.transfer(tokenId.toString(), auctionAccount.toString());

        JsonObject auction = new JsonObject();
        auction.put("tokenid", tokenId.toString());
        auction.put("auctionaccountid", auctionAccount.toString());
        auction.put("reserve", 0);
        auction.put("minimumbid", 10);
        auction.put("winnercanbid", true);

        // store auction data in initDemo.json file
        FileWriter myWriter = new FileWriter("./sample-files/initDemo.json", UTF_8);
        myWriter.write(auction.encodePrettily());
        myWriter.close();

        log.info("*************************");
        log.info(" ./sample-files/initDemo.json file written");

        CreateAuction.create("./sample-files/initDemo.json", topicId);
    }
}
