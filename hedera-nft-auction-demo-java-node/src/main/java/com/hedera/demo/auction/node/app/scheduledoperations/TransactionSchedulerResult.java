package com.hedera.demo.auction.node.app.scheduledoperations;

import com.hedera.hashgraph.sdk.ScheduleId;
import com.hedera.hashgraph.sdk.Status;

public class TransactionSchedulerResult {
    public boolean success;
    public Status status;
    public ScheduleId scheduleId;

    public TransactionSchedulerResult(boolean success, Status status, ScheduleId scheduleId) {
        this.success = success;
        this.status = status;
        this.scheduleId = scheduleId;
    }
}
