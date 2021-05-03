package com.hedera.demo.auction.node.app.auctionendtokentransfer;

import com.hedera.demo.auction.node.app.HederaClient;
import com.hedera.demo.auction.node.app.repository.AuctionsRepository;
import io.vertx.ext.web.client.WebClient;

public abstract class AbstractAuctionEndTokenTransfer {

    protected final WebClient webClient;
    protected final AuctionsRepository auctionsRepository;
    protected final String tokenId;
    protected final String winningAccountId;
    protected final String mirrorURL;
    protected final HederaClient hederaClient;

    protected AbstractAuctionEndTokenTransfer(HederaClient hederaClient, WebClient webClient, AuctionsRepository auctionsRepository, String tokenId, String winningAccountId) {
        this.webClient = webClient;
        this.auctionsRepository = auctionsRepository;
        this.tokenId = tokenId;
        this.winningAccountId = winningAccountId;
        this.hederaClient = hederaClient;
        this.mirrorURL = hederaClient.mirrorUrl();
    }
}