package com.hedera.demo.auction.node.app.scheduledoperations;

import com.google.errorprone.annotations.Var;
import com.hedera.demo.auction.node.app.HederaClient;
import com.hedera.demo.auction.node.app.Utils;
import com.hedera.demo.auction.node.app.domain.Auction;
import com.hedera.demo.auction.node.app.domain.ScheduledOperation;
import com.hedera.demo.auction.node.app.repository.AuctionsRepository;
import com.hedera.demo.auction.node.app.repository.ScheduledOperationsRepository;
import com.hedera.hashgraph.sdk.*;
import lombok.extern.log4j.Log4j2;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Log4j2
public class ScheduleExecutor implements Runnable {
    private final HederaClient hederaClient;
    private final AuctionsRepository auctionsRepository;
    private final ScheduledOperationsRepository scheduledOperationsRepository;
    private final PrivateKey auctionKey;
    @Var private int queryFrequency = 10 * 1000;
    private boolean runThread = true;

    public ScheduleExecutor(HederaClient hederaClient, AuctionsRepository auctionsRepository, ScheduledOperationsRepository scheduledOperationsRepository, PrivateKey auctionKey) {
        this.hederaClient = hederaClient;
        this.auctionsRepository = auctionsRepository;
        this.scheduledOperationsRepository = scheduledOperationsRepository;
        this.auctionKey = auctionKey;
    }

    @Override
    public void run() {
        while (runThread) {
            try {
                queryFrequency = 10 * 1000;
                List<ScheduledOperation> pendingOperations = scheduledOperationsRepository.getPendingOperationsList();
                if (pendingOperations != null) {
                    for (ScheduledOperation scheduledOperation : pendingOperations) {
                        try {
                            Auction auction = auctionsRepository.getAuction(scheduledOperation.getAuctionid());
                            AccountId auctionAccountId = AccountId.fromString(auction.getAuctionaccountid());

                            if (scheduledOperation.getTransactiontype().equals(ScheduledOperation.TYPE_TOKENASSOCIATE)) {
                                // create a token association transaction
                                TokenAssociateTransaction tokenAssociateTransaction = new TokenAssociateTransaction();
                                List<TokenId> tokenIds = new ArrayList<>();
                                tokenIds.add(TokenId.fromString(auction.getTokenid()));
                                tokenAssociateTransaction.setTokenIds(tokenIds);
                                tokenAssociateTransaction.setTransactionMemo(scheduledOperation.getMemo());
                                tokenAssociateTransaction.setAccountId(auctionAccountId);
                                tokenAssociateTransaction.setMaxTransactionFee(Hbar.from(100));

                                String transactionIdString = auction.getAuctionaccountid().concat("@").concat(scheduledOperation.getTransactiontimestamp());
                                TransactionId transactionId = TransactionId.fromString(transactionIdString);

                                TransactionScheduler transactionScheduler = new TransactionScheduler(hederaClient.client(), auctionAccountId, auctionKey, transactionId, tokenAssociateTransaction);
                                try {
                                    TransactionSchedulerResult transactionSchedulerResult = transactionScheduler.issueScheduledTransaction();
                                    if (transactionSchedulerResult.success) {
                                        scheduledOperation.setStatus(ScheduledOperation.EXECUTING);
                                        log.info("token associate transaction successfully scheduled (id " + transactionIdString + ")");
                                        scheduledOperationsRepository.setStatus(scheduledOperation.getTransactiontimestamp(), ScheduledOperation.EXECUTING, "");
                                    } else if (transactionSchedulerResult.status == Status.TRANSACTION_EXPIRED) {
                                        String newTimeStamp = Utils.addToTimestamp(scheduledOperation.getTransactiontimestamp(), 30);
                                        scheduledOperationsRepository.setTimestamp(scheduledOperation.getTransactiontimestamp(), newTimeStamp);
                                        queryFrequency = 1 * 1000;
                                        log.info("token associate transaction re-scheduled for timestamp " + newTimeStamp);
                                    } else {
                                        log.error("error scheduling token associate transaction (timestamp " + scheduledOperation.getTransactiontimestamp() + ")");
                                        scheduledOperationsRepository.setStatus(scheduledOperation.getTransactiontimestamp(), ScheduledOperation.PENDING, transactionSchedulerResult.status.toString());
                                        log.error(transactionSchedulerResult.status);
                                    }

                                } catch (TimeoutException timeoutException) {
                                    timeoutException.printStackTrace();
                                }
                            }
                        } catch (SQLException e) {
                            log.error("error fetching auction for pending operations");
                            log.error(e);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (SQLException sqlException) {
                log.error("error fetching list of pending operations");
                log.error(sqlException);
            }

            try {
                Thread.sleep(queryFrequency);
            } catch (InterruptedException e) {
                log.error(e);
            }
        }
    }

    public void stop() {
        runThread = false;
    }
}
