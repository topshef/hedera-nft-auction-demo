package com.hedera.demo.auction.node.test.system.e2eApp;

import com.google.errorprone.annotations.Var;
import com.hedera.demo.auction.node.app.App;
import com.hedera.demo.auction.node.app.HederaClient;
import com.hedera.demo.auction.node.app.SqlConnectionManager;
import com.hedera.demo.auction.node.app.domain.Auction;
import com.hedera.demo.auction.node.app.repository.AuctionsRepository;
import com.hedera.demo.auction.node.app.repository.BidsRepository;
import com.hedera.demo.auction.node.test.system.AbstractSystemTest;
import com.hedera.hashgraph.sdk.*;
import lombok.extern.log4j.Log4j2;
import net.joshka.junit.json.params.JsonFileSource;
import org.jooq.tools.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.awaitility.Awaitility.await;

@Log4j2
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class E2EAppTest extends AbstractSystemTest {

    App app = new App();
    long auctionReserve;
    long auctionMinimumBid;
    boolean auctionWinnerCanBid;
    Hbar startBalance;
    HederaClient balanceHederaClient = new HederaClient();

    E2EAppTest() throws Exception {
        super();
    }

    @BeforeAll
    public void beforeAll() throws Exception {
        postgres = new PostgreSQLContainer("postgres:12.6");
        postgres.start();
        migrate(postgres);
        SqlConnectionManager connectionManager = new SqlConnectionManager(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        auctionsRepository = new AuctionsRepository(connectionManager);
        bidsRepository = new BidsRepository(connectionManager);
        biddingAccounts = new HashMap<>();

        AccountBalance balance = new AccountBalanceQuery()
                .setAccountId(balanceHederaClient.operatorId())
                .execute(balanceHederaClient.client());

        startBalance = balance.hbars;
    }

    @AfterAll
    public void afterAll() throws TimeoutException, PrecheckStatusException {

        this.postgres.close();

        AccountBalance balance = new AccountBalanceQuery()
                .setAccountId(balanceHederaClient.operatorId())
                .execute(balanceHederaClient.client());

        Hbar endBalance = balance.hbars;

        long testCost = startBalance.toTinybars() - endBalance.toTinybars();

        log.info("System test cost in hbar: " + Hbar.fromTinybars(testCost));
    }

    @BeforeEach
    public void beforeEach() throws Exception {
        bidsRepository.deleteAllBids();
        auctionsRepository.deleteAllAuctions();
        maxBid = 0;
        accountBalances = new HashMap<>();
    }

    private void setupTest(JsonValue mirrorValue, JsonObject test) throws Exception {
        String mirror = ((JsonString) mirrorValue).getString();
        log.info("  Using mirror " + mirror);

        hederaClient = new HederaClient(dotenv);

        transferOnWin = test.getBoolean("transferOnWin", true);

        // create a topic
        createTopicAndGetInfo();

        // create an auction account
        io.vertx.core.json.JsonObject jsonThresholdKey = jsonThresholdKey(1, auctionAccountKey);
        io.vertx.core.json.JsonObject keysCreate = new io.vertx.core.json.JsonObject().put("keylist", new io.vertx.core.json.JsonArray().add(jsonThresholdKey));

        createAccountAndGetInfo(keysCreate.toString());
        log.info("Auction account " + auctionAccountId.toString());

        // create token
        String tokenSymbolFromJson = test.getString("symbol", "");
        createTokenAndGetInfo(tokenSymbolFromJson);

        hederaClient.setMirrorProvider(mirror);
        hederaClient.setClientMirror();
        hederaClient.setOperator(auctionAccountId, auctionAccountKey);

        app.overrideEnv(hederaClient, /* restAPI= */true, /* adminAPI= */true, /* auctionNode= */true, topicId.toString(), auctionAccountKey.toString(), postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), /* transferOnWin= */transferOnWin);
    }

    private void bidOnBehalfOf(String from, long amount, boolean expectFail) throws TimeoutException, PrecheckStatusException, ReceiptStatusException {
        // if account doesn't exist create it
        if (! biddingAccounts.containsKey(from)) {
            TransactionResponse response = new AccountCreateTransaction()
                    .setKey(bidAccountKey.getPublicKey())
                    .setInitialBalance(Hbar.from(10))
                    .execute(testRunnerClient);

            TransactionReceipt receipt = response.getReceipt(testRunnerClient);
            biddingAccounts.put(from, receipt.accountId);
        }

        if (! expectFail) {
            setMaxBid(amount, biddingAccounts.get(from));
        }

        // transfer the necessary amount to the bidding account
        @Var TransactionResponse response = new TransferTransaction()
                .addHbarTransfer(biddingAccounts.get(from), Hbar.fromTinybars(amount))
                .addHbarTransfer(testRunnerClient.getOperatorAccountId(), Hbar.fromTinybars(-amount))
                .execute(testRunnerClient);

        response.getReceipt(testRunnerClient);

        // transfer bid
        bidderClient.setOperator(biddingAccounts.get(from), bidAccountKey);
        response = new TransferTransaction()
                .addHbarTransfer(biddingAccounts.get(from), Hbar.fromTinybars(-amount))
                .addHbarTransfer(auctionAccountId, Hbar.fromTinybars(amount))
                .execute(bidderClient);

        response.getReceipt(bidderClient);
    }

    private void associateOnBehalfOfWinner() throws TimeoutException, PrecheckStatusException, ReceiptStatusException {
        bidderClient.setOperator(maxBidAccount, bidAccountKey);
        TransactionResponse response = new TokenAssociateTransaction()
                .setAccountId(maxBidAccount)
                .setTokenIds(List.of(tokenId))
                .execute(bidderClient);

        response.getReceipt(bidderClient);
    }

    private void runTestTasks(JsonValue mirrorValue, JsonObject test, JsonArray tasks) throws Exception {
        for (JsonValue taskJson : tasks) {
            JsonObject task = taskJson.asJsonObject();
            String taskName = task.getString("name", "");
            String taskDescription = task.getString("description", "");
            String account = task.getString("account", "");

            log.info("  running task " + taskName + " - " + taskDescription);

            switch (taskName) {
                case "startApp":
                    app.runApp();
                    break;
                case "stopApp":
                    app.stop();
                    // wait a few seconds for the app to close fully
                    await()
                        .with()
                        .pollDelay(Duration.ofSeconds(5))
                        .await()
                        .until(alwaysTrueForDelay());
                    break;
                case "createAuction":
                    auctionReserve = task.getJsonNumber("reserve").longValue();
                    auctionMinimumBid = task.getJsonNumber("minimumBid").longValue();
                    auctionWinnerCanBid = task.getBoolean("winnerCanBid");
                    createAuction(auctionReserve, auctionMinimumBid, auctionWinnerCanBid);
                    break;
                case "getAuction":
                    int index = task.getInt("index");
                    // query repository for auctions
                    List<Auction> auctionsList = auctionsRepository.getAuctionsList();
                    auction = auctionsList.get(index);
                    break;
                case "transferToken":
                    // transfer the token to the auction
                    transferToken();
                    break;
                case "bid":
                    String from = task.getString( "from", "");
                    long amount = task.getJsonNumber("amount").longValue();
                    boolean expectFail = task.getBoolean("expectFail", false);
                    bidOnBehalfOf(from, amount, expectFail);
                    break;
                case "endAuction":
                    auctionsRepository.setEndTimestampForTesting(auction.getId());
                    // in-task assertions are handled after the switch case
                    break;
                case "winnerAssociate":
                    associateOnBehalfOfWinner();
                    break;
                case "getBalance":
                    getBalanceForAccount(account);
                    break;
                case "assertions":
                    // in-task assertions are handled after the switch case
                    break;
                default:
                    log.error("unknown task " + task);
            }
            assertTask(task);
        }

        JsonArray assertions = test.getJsonArray("assertions");
        if (assertions != null) {
            for (JsonValue assertionJson : assertions) {
                JsonObject assertion = assertionJson.asJsonObject();
                assertValue(assertion);
            }
        }
    }

    @ParameterizedTest
    @JsonFileSource(resources = "/e2eSystemTests.json")
    public void e2eTest(JsonObject test) throws Exception {
        log.info("Starting e2e test : " + test.getString("testName"));

        JsonArray mirrors = test.getJsonArray("mirrors");

        for (JsonValue mirrorValue : mirrors) {

            setupTest(mirrorValue, test);
//
//            App app = new App();
//            app.overrideEnv(hederaClient, /* restAPI= */true, /* adminAPI= */true, /* auctionNode= */true, topicId.toString(), auctionAccountKey.toString(), postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), /* transferOnWin= */transferOnWin);
//            app.runApp();

            // wait for auction to appear in database
//            await()
//                    .with()
//                    .pollInterval(Duration.ofSeconds(1))
//                    .await()
//                    .atMost(Duration.ofSeconds(20))
//                    .until(auctionsCountMatches(1));

            JsonArray tasks = test.getJsonArray("tasks");

            runTestTasks(mirrorValue, test, tasks);

            app.stop();

        }
    }

    private void assertTask(JsonObject task) throws SQLException {
        JsonArray assertions = task.getJsonArray("assertions");
        if (assertions == null) {
            return;
        }
        for (JsonValue assertionJson : assertions) {
            JsonObject assertion = assertionJson.asJsonObject();

            assertValue(assertion);
        }
    }

    private void getBalanceForAccount(String account) throws TimeoutException, PrecheckStatusException {
        @Var AccountId accountId;
        switch (account) {
            case "tokenOwner":
                accountId = tokenOwnerAccountId;
                break;
            case "auctionAccount":
                accountId = auctionAccountId;
                break;
            case "winner":
                accountId = maxBidAccount;
                break;
            default:
                log.info("Invalid account " + account + " for getBalance task");
                return;
        }

        AccountBalance balance = new AccountBalanceQuery()
                .setAccountId(accountId)
                .execute(testRunnerClient);

        accountBalances.put(account, balance.hbars.toTinybars());
    }

    private void assertValue(JsonObject assertion) throws SQLException {
        log.info("  asserting " + assertion.toString());

        String object = assertion.getString("object", "");
        String parameter = assertion.getString( "parameter", "");
        String condition = assertion.getString("condition", "equals");

        @Var String value = assertion.getString( "value", "");
        String from = assertion.getString("from", "");

        @Var String bidAccount = "";
        @Var long bidAmount = 0;

        if (StringUtils.isEmpty(value)) {
            if (parameter.equals("winningAccount") && condition.equals("equals")) {
                value = maxBidAccount.toString();
            } else if (parameter.equals("winningBid") && condition.equals("equals")) {
                value = String.valueOf(maxBid);
            } else if (parameter.equals("bidderAccountId") && condition.equals("equals")) {
                value = String.valueOf(biddingAccounts.get(assertion.getString("from")));
            } else if (parameter.equals("bidAmount") && condition.equals("equals")) {
                value = String.valueOf(assertion.getJsonNumber("amount").longValue());
            } else if (parameter.equals("tokenOwnerAccountId")) {
                value = tokenOwnerAccountId.toString();
            }
        }

        if (object.equals("bid")) {
            if (! StringUtils.isEmpty(from)) {
                bidAccount = biddingAccounts.get(from).toString();
            }
            if (assertion.containsKey("amount")) {
                bidAmount = assertion.getJsonNumber("amount").longValue();
            }
        }

        switch (object) {
            case "auction":
                if (parameter.equals("count")) {
                    await()
                            .with()
                            .pollInterval(Duration.ofSeconds(1))
                            .await()
                            .atMost(Duration.ofSeconds(20))
                            .until(auctionsCountMatches(Integer.parseInt(value)));

                } else {
                    await()
                            .with()
                            .pollInterval(Duration.ofSeconds(1))
                            .await()
                            .atMost(Duration.ofSeconds(30))
                            .until(auctionValueAssert(parameter, value, condition));
                }
                break;
            case "bid":
                await()
                        .with()
                        .pollInterval(Duration.ofSeconds(1))
                        .await()
                        .atMost(Duration.ofSeconds(30))
                        .until(bidValueAssert(bidAccount, bidAmount, parameter, value, condition));
                break;
            case "balance":
                await()
                        .with()
                        .pollInterval(Duration.ofSeconds(1))
                        .await()
                        .atMost(Duration.ofSeconds(20))
                        .until(checkBalance(parameter, condition));
                break;
            case "winnerOwnsToken":
                await()
                        .with()
                        .pollDelay(Duration.ofSeconds(20))
                        .and()
                        .with()
                        .timeout(Duration.ofSeconds(30))
                        .await()
                        .until(tokenTransferred(maxBidAccount));
                break;
            case "winnerDoesNotOwnToken":
                await()
                        .with()
                        .pollDelay(Duration.ofSeconds(20))
                        .and()
                        .with()
                        .timeout(Duration.ofSeconds(30))
                        .await()
                        .until(tokenNotTransferred(maxBidAccount));
                break;
            case "issuerOwnsToken":
                await()
                        .with()
                        .pollDelay(Duration.ofSeconds(20))
                        .and()
                        .with()
                        .timeout(Duration.ofSeconds(30))
                        .await()
                        .until(tokenTransferred(tokenOwnerAccountId));
                break;
            case "issuerDoesNotOwnToken":
                await()
                        .with()
                        .pollDelay(Duration.ofSeconds(20))
                        .and()
                        .with()
                        .timeout(Duration.ofSeconds(30))
                        .await()
                        .until(tokenNotTransferred(tokenOwnerAccountId));
                break;
            default:
                log.error("unknown assertion " + object);
        }
    }
}
