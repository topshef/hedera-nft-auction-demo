[![Java CI with Gradle](https://github.com/hashgraph/hedera-nft-auction-demo/actions/workflows/unit-integration-test.yml/badge.svg)](https://github.com/hashgraph/hedera-nft-auction-demo/actions/workflows/unit-integration-test.yml)
[![codecov](https://img.shields.io/codecov/c/github/hashgraph/hedera-nft-auction-demo/master)](https://codecov.io/gh/hashgraph/hedera-nft-auction-demo)
[![GitHub](https://img.shields.io/github/license/hashgraph/hedera-nft-auction-demo)](LICENSE)
[![Discord](https://img.shields.io/badge/discord-join%20chat-blue.svg)](https://hedera.com/discord)

# Hedera Non Fungible Token Auction Demo

## Dependencies

* A testnet or mainnet account
* PostgreSQL version 12
* Node.js v14.9.0
* Yarn 1.22.10
* Java 14
* Docker and docker-compose (optional)

## Notes

The java projects use Lombok, ensure that the plug is installed in your IDE and configured properly [Lombok Plugin](https://www.baeldung.com/lombok-ide)

_Note that enabling annotation processing differs between versions of IntelliJ `Preferences > Compiler > Annotation Processors` before IntelliJ2017, starting with IntelliJ 2017, the "Enable Annotation Processing" checkbox has moved to: `Settings > Build, Execution, Deployment > Compiler > Annotation Processors`_

## Description

This project consists of two main modules, a Java back-end and a front-end.

The front end displays auctions and enables users to place bids (optionally requires a browser plug in to sign transactions) and monitor the auction's progress.

The back end fulfils 3 separate roles

* UI REST API server for the UI above
* Admin REST API server for admin features (such as creating a new auction)
* Auction and Bid processing, registering new auctions, checking bid validity, issuing refunds

The latter can be run in readonly mode, meaning the processing will validate bids but will not be able to participate in refunds or token transfers on auction completion.

_Note: The three roles can be run within a single instance of the Java application or run in individual Java instances, for example, one instance could process bids, while one or more others could serve the UI REST API for scalability purposes. This is determined by environment variable parameters.
The Docker deployment runs two instances, one for bid processing, the other for the UI and Admin REST APIs for example._

The admin API runs on a separate port to the UI REST API to ensure it can be firewalled separately and protected from malicious execution.

## Setup, compilation, execution

Pull the repository from github

```shell
git clone https://github.com/hashgraph/hedera-nft-auction-demo.git
```

### With docker

#### Setup environment

```shell
cd hedera-nft-auction-demo
cd docker-files
cp .env.sample .env
nano .env 
```

setup the `.env` properties as follows

* `OPERATOR_ID=` (input your account id for the Hedera network)
* `OPERATOR_KEY=` (input your private key associated with the Hedera account above - 302xxxx)
* `VUE_APP_NETWORK=` (mainnet, testnet or previewnet)
* `REFUND_KEY=` (Same as operator key for testing purposes)
* `MASTER_KEY=` (set only for one node which has additional authority over the auction accounts, can be the same as operator key / refund key for testing purposes only, else must be different)

you may leave the other properties as is for now

```shell
cd hedera-nft-auction-demo
cd hedera-nft-auction-demo-javascript-client
cp .env.sample .env
```

you may leave the properties as is for now

#### Start docker images

```shell
cd ..
docker-compose build
docker-compose up
```

you may now navigate to [http://localhost:8080](http://localhost:8080) to verify the UI is up and running, it should indicate no auctions are currently setup.

#### Create a sample auction

```shell script
curl -H "Content-Type: application/json" -X POST -d '{}' http://localhost:8082/v1/admin/easysetup
```

#### Restart the docker containers for the topic to be taken into account

Stop the containers with `CTRL+C`

Restart the containers

```shell script
docker-compose up
```

You should see logs similar to

`
nft-auction-demo-node | 2021-04-23 12:26:27.063 INFO  com.hedera.demo.auction.node.app.subscriber.TopicSubscriber - Auction for token 0.0.539174 added (150)
nft-auction-demo-node | 2021-04-23 12:26:27.063 INFO  com.hedera.demo.auction.node.app.subscriber.TopicSubscriber - Auction for token 0.0.539174 added (150)
nft-auction-demo-node | 2021-04-23 12:26:29.022 INFO  com.hedera.demo.auction.node.app.readinesswatcher.HederaAuctionReadinessWatcher - Watching auction account Id 0.0.539175, token Id 0.0.539174 (36)
nft-auction-demo-node | 2021-04-23 12:26:29.024 DEBUG com.hedera.demo.auction.node.app.readinesswatcher.HederaAuctionReadinessWatcher - Checking ownership of token 0.0.539174 for account 0.0.539175 (52)
nft-auction-demo-node | 2021-04-23 12:26:29.364 INFO  com.hedera.demo.auction.node.app.readinesswatcher.AbstractAuctionReadinessWatcher - Account 0.0.539175 owns token 0.0.539174, starting auction (70)
nft-auction-demo-node | 2021-04-23 12:26:39.380 DEBUG com.hedera.demo.auction.node.app.bidwatcher.HederaBidsWatcher - Checking for bids on account 0.0.539175 and token 0.0.539174 (38)
...
`

you may now navigate to [http://localhost:8080](http://localhost:8080) to verify the UI is up and running, it should show the auction created above (it may take a few seconds to appear).

#### Notes

* EasySetup which is invoked to create an auction deletes all the data from the database, creates a new topic and a new auction. Make sure you restart the containers after running this easySetup.

* Database files are persisted on your host under `docker-files\postgres-data`, to completely delete the database, delete this folder and restart the containers.

* The `docker-files` folder is mounted as a volume on the containers.

### Standalone

#### Database

All database objects will be created in the `public` database.

_Note the installation below assumes the user is `postgres` and the password is `password`._

#### Java Appnet Node

```shell
cd hedera-nft-auction-demo
cd hedera-nft-auction-demo-java-node

# Build the code
./gradlew assemble
```

setup the environment

```shell
cp .env.sample .env
nano .env
```

set the following properties according to your Hedera account and refund key details

_Note: 
The operator id/key is used to query the hedera network (free queries) 
* It is also used to set the submit key for the auction topic and also for creating the auction account, submitting auction creation messages to the topic.
* And optionally creating a token to auction, then transferring it to the auction account_

* `OPERATOR_ID=` (input your account id for the Hedera network)
* `OPERATOR_KEY=` (input your private key associated with the Hedera account above - 302xxxx)
* `REFUND_KEY=` (Same as operator key for testing purposes)
* `TRANSFER_ON_WIN=`true

You may edit additional parameters such as `MIRROR_PROVIDER`, etc... if you wish

#### Javascript UI

```shell
cd hedera-nft-auction-demo
cd hedera-nft-auction-demo-javascript-client
# Build the code
./yarn install
```

Edit environment variables

```shell
cp .env.sample .env
nano .env
```

* `VUE_APP_API_PORT=8081` this is the port of the `Java REST API` above
* `VUE_APP_NETWORK=testnet` previewnet, testnet or mainnet
* `VUE_APP_TOPIC_ID=` topic id the appnet is using
* `PORT=8080` the port you want to run the UI on
* `VUE_APP_NODE_OWNER` optionally set the name of the company operating the node to display in the UI

#### Setting up an auction

A number of helper functions are available from the project in order to get you started quickly.

*Note, this section assumes you are running the commands from the `hedera-nft-auction-demo-java-node` directory.*

```shell
cd hedera-nft-auction-demo
cd hedera-nft-auction-demo-java-node
```

#### Super simple

This command takes a number of parameters runs all the necessary steps to create a demo auction:

* create a HCS Topic
* create a simple token
* create an auction account
* create an auction file
* setup the auction
* transfers the token to the auction

__Parameters__

The following parameter are optional and defaulted if not supplied 

*note, the database will be cleared and a new topic created unless `--no-clean` is provided*

* --name, token's name
* --symbol, this will determine the symbol for the token, if the symbol refers to a file path, a Hedera file entity will be created with the contents and the token's symbol set to the file id 
* --no-clean, do no create a new topic and do not delete data from the database 

__Command line__

```shell
./gradlew easySetup
```

*Note: the application wil need to be restarted to take the new topic into account*

```shell
./gradlew easySetup --args="--name=myToken --symbol=MTT --no-clean"
```

__REST API__

This requires that the REST api and database are up and running

```shell script
curl -H "Content-Type: application/json" -X POST -d '{}' http://localhost:8082/v1/admin/easysetup
```

or

```shell script
curl -H "Content-Type: application/json" -X POST -d '{"symbol":"./sample-files/gold-base64.txt","name":"Test Token","clean":false, "title": "Auction Title", "description": "Auction description" }' http://localhost:8082/v1/admin/easysetup
```

#### Step by step via command line

These steps will enable you to create an `initDemo.json` file (located in `./sample-files`) which you can finally use to setup a new auction.

_Note: the application will need to be restarted to take the new topic into account_

__Create a topic__

```shell
./gradlew createTopic
```

__Create a simple token__

This command will create a token named `test` with a symbol of `tst`, an initial supply of `1` and `0` decimals.

```shell
./gradlew createToken --args="test tst 1 0 TokenMemoHere"
```

set the resulting `Token Id` to the `tokenId` attribute in your `./sample-files/initDemo.json` file.

__Create an auction account__

This command will create an auction account with an initial balance of `100` hbar using the operator key for the account.

```shell
./gradlew createAuctionAccount --args="100"
```

_Note: For more complex key structures, use the REST admin api._

set the resulting `Account Id` to the `auctionaccountid` attribute in your `./sample-files/initDemo.json` file.

__Finalising the initDemo.json file__

Your initDemo.json file should look like this (with your own values).

You can change some of the attribute values if you wish

_Note: if the `endtimestamp` (end of auction in seconds since Epoch) is left blank, the auction will run for 48 hours from now by default._

```json
{
  "tokenid": "0.0.xxxxxx",
  "auctionaccountid": "0.0.yyyyyy",
  "endtimestamp": "",
  "reserve": 0,
  "minimumbid": 0,
  "title": "Auction title",
  "description": "Auction description"
}
```

_Note: the minimum bid and reserve are expressed in `tinybars`_

__Create the auction__
```shell
./gradlew createAuction --args="./sample-files/initDemo.json"
```

__Transfer the token to the auction account__

This transfer the token from the account that created it to the `auctionaccountid`, supply the `tokenId` and `accountId` created above in the parameters.

```shell
./gradlew createTokenTransfer --args="tokenId accountId"
```

#### Step by step via REST API

This requires that the REST api and database are up and running. 

The examples below show curl commands, however the `hedera-nft-auction-demo-java-node` project includes `postman` files for the admin and client APIS which you can import into Postman instead.

__Create a topic__

_Note: the application wil need to be restarted to take the new topic into account_

```shell script
curl -H "Content-Type: application/json" -X POST -d '
  {
  }
' http://localhost:8082/v1/admin/topic
```

returns a topic id

```json
{
    "topicId": "0.0.57044"
}
```

__Create a simple token__

This command will create a token named `test` with a symbol of `tst`, an initial supply of `1`, `0` decimals and add a memo to the token.

```shell script
curl -H "Content-Type: application/json" -X POST -d '
{
  "name": "test", 
  "symbol":"tst", 
  "initialSupply": 1, 
  "decimals": 0,
  "memo": "memo for the token"
}
' http://localhost:8082/v1/admin/token
```

returns a token id

```json
{
    "tokenId": "0.0.58792"
}
```

__Create an auction account__

This command will create an auction account with an initial balance of `100` hbar and use the operator key for the account.

```shell script
curl -H "Content-Type: application/json" -X POST -d '
  {
    "initialBalance": 100
  }
' http://localhost:8082/v1/admin/auctionaccount
```

returns an account id

```json
{
    "accountId": "0.0.58793"
}
```

__Create an auction account with a key list__

This command will create an auction account with an initial balance of `100` hbar and a key list for scheduled transactions.

_Note: the first key should be the "master key" which can sign transactions on behalf of the auction account for transaction types that can't be scheduled for now such as TokenAssociate and CryptoUpdate.
In the example below, we have a key list with a threshold of 1. The key list contains the master key and another list of keys with its own threshold.
_

```shell script
curl -H "Content-Type: application/json" -X POST -d '
{
  "keyList" : {
    "keys": [
      {
        "key" : "302a300506032b65700321001481572a21874fb9da18b49f0265aca8d94f435a879c0d6631b8ce54d96dc58c"
      },
      {
        "keyList": {
          "keys": [
            {
              "key": "302a300506032b657003210090ec5045925d37b358ee0c60f858dc79c3b4370cbf7e0c5dad882f1171265cb3"
            },
            {
              "key": "302a300506032b657003210076045799d169c6b6fc2bf45f779171a1cb10fd239b4f758bc556cb0de6799105"
            }
          ],
          "threshold": 1
        }
      }
    ],
    "threshold" : 1
  },
  "initialBalance": 100
}' http://localhost:8082/v1/admin/auctionaccount
```

returns an account id

```json
{
    "accountId": "0.0.58793"
}
```


__Create the auction__

be sure the replace `{{tokenId}}`, `{{accountId}}` in the json below with the values you obtained earlier.

```shell script
curl -H "Content-Type: application/json" -X POST -d '
{
  "tokenid": "{{tokenId}}", 
  "auctionaccountid": "{{accountId}}", 
  "reserve": "", 
  "minimumbid": "1000000", 
  "endtimestamp": "", 
  "winnercanbid": true,
  "title": "Auction title",
  "description": "Auction description"
}' http://localhost:8082/v1/admin/auction
```

_Note: the minimum bid and reserve are expressed in `tinybars`_

__Transfer the token to the auction account__

This transfer the token from the account that created it to the `auctionaccountid`, supply the `tokenId` and `accountId` created above in the parameters.

be sure the replace `{{tokenId}}`, `{{accountId}}` in the json below with the values you obtained earlier.

```shell script
curl -H "Content-Type: application/json" -X POST -d '
{
  "tokenid" : "{{tokenId}}", 
  "auctionaccountid" : "{{accountId}}"
}' http://localhost:8082/v1/admin/transfer
```

#### Run the components

_Note: Each of the steps below need to be run from a different command line window_

```shell
cd hedera-nft-auction-demo
```

__Appnet node and REST API__

```shell
cd hedera-nft-auction-demo-java-node
java -jar build/libs/hedera-nft-auction-demo-1.0.jar
```

__Web UI__

```shell
cd hedera-nft-auction-demo-javascript-client
yarn serve
```

## Developing new features requiring database changes

This is only required in order to create/modify database objects, it happens automatically when the application is launched too.

__Setup the database objects__

```shell script
./gradlew flywayMigrate
````

__Build the database classes__

```shell script
./gradlew jooqGenerate
````

## Testing

The project contains a number of tests suites which are described below

### Unit testing

Unit testing is run automatically when you `./gradlew build`, you can run these independently with `./gradlew test`. 

There are no interactions with the database or the Hedera network in these tests.

### Integration testing

Integration testing is run automatically when you `./gradlew build`, you can run these independently with `./gradlew testIntegration`.

These tests include testing the outcome of various operations in the database.

### System testing

System testing is run with `./gradlew testSystem`.

These tests include testing the outcome of various operations in the database and invoke Hedera APIs.

# Full Application network setup

The back-end can be considered a node within the application network, there may be several such nodes in operation to ensure full decentralisation of the auction process. Such nodes fall into three categories:

* `Readonly Node`: This node type is not actively participating in the running of the auctions, but may be run by any third party to verify the correct running of the auctions.

* `Validator Node`: This node type is actively participating in the running of the auctions, it holds a private key which is used to counter-sign transactions on behalf of the entire application network. Indeed, an auction account is created for each auction, and this account is multi-sig. Depending on the threshold set on the auction account's key, a minimum number of signatures from validator nodes is required in order to approve operations such as refunds, token transfers, etc...

* `Master Node`: This node is functionally equivalent to the `Validator Node` above, except that it has the ability to associate the auction accounts with tokens and set the auction account's `signature required` option when an auction ends to prevent further bids from being placed.

_Note: This `Master Node` will eventually become a `Validator Node` in its own right once the whitelisting of the `Token Associate` and `Account Update` transactions has occurred. It is a temporary solution in the mean time._

## Determining the node type

Depending on the type of node you're setting up, you may need different information or may need to submit some information to other node operators.

### Readonly Node

You must acquire the `Topic Id` for the application network from the entity that setup the application network in the first place. This `Topic Id` is used by the application network to share details of new auctions being created.

### Validator Node

You must acquire the `Topic Id` as described above for a `Readonly Node`.

In addition, you'll need to generate an ED25519 private/public key and share the public key with whoever is setting up an auction for you to validate.

### Master Node

This node will be creating the `Topic Id` to share with the `Readonly` and `Validator` Nodes.

Two ED25519 private/public keys will be required, one will be the `MASTER_KEY`, the other the `REFUND_KEY` for your node. Both public keys shared with whoever is setting up an auction.

### Generating keys

A helper function is available to generate keys as follows

```shell
./gradlew generateKey
```

or 

```shell script
curl -H "Content-Type: application/json" -X POST -d '{}' http://localhost:8081/v1/generatekey
```

_Note: this runs on the client REST API port (8081), not the admin API port (8082)

## Environment setup 

### All node types

* `OPERATOR_ID=` (input your account id for the Hedera network)
* `OPERATOR_KEY=` (input your private key associated with the Hedera account above - 302xxxx)
* `VUE_APP_NETWORK=` (mainnet, testnet or previewnet)
* `VUE_APP_NODE_OWNER=` (an identifier, e.g. `ACMEAuctions` to be rendered in the UI to show which node the UI is connected to)
* `VUE_APP_TOPIC_ID=` (the topic id provided by whoever is setting up the application network, leave blank if you're setting up a new application network)

### Validator nodes

in addition to all node types above

* `REFUND_KEY=` The ED25519 private key you generated
* `TRANSFER_ON_WIN=` true or false depending on whether you want the auction to transfer the tokens and winning bid automatically at the end.

### Master node

in addition to all node types above

* `REFUND_KEY=` The ED25519 private key you generated
* `MASTER_KEY=` The ED25519 private key you generated (set only for one node which has additional authority over the auction accounts, can be the same as operator key / refund key for testing purposes only, else must be different)
* `TRANSFER_ON_WIN=` true or false depending on whether you want the auction to transfer the tokens and winning bid automatically at the end.

## Creating the topic ID to share with the rest of the network

From the command line of your node (assuming the admin API is enabled)

```shell
curl -H "Content-Type: application/json" -X POST -d '
{
}
' http://localhost:8082/v1/admin/topic
```

This will create and output a topic id and will also update your `.env` file with its value. You may now share this topic Id with the rest of the application network participants.

## Creating a token to auction

You may now create a token to auction, see documentation above for helpers if you're not sure how to do this.

## Creating an account for a token auction

This action needs to be performed for every new token to be auctioned, the same account cannot be used for two different tokens, the application will reject the auction creation if this is the case.

This command will create an auction account with an initial balance of `100` hbar, and a key list for scheduled transactions.

_Note: the first key should be the "master key" which can sign transactions on behalf of the auction account for transaction types that can't be scheduled for now such as TokenAssociate and CryptoUpdate.
In the example below, we have a key list with a threshold of 1. The key list contains the master key and another list of keys with its own threshold of 2._

* Replace "public master key" with the appropriate value

* Replace and add "validator n public key" as required

* Set threshold on the inner key list as required (leave the last threshold before `initialBalance` to 1).

```shell script
curl -H "Content-Type: application/json" -X POST -d '
{
  "keyList" : {
    "keys": [
      {
        "key" : "public master key"
      },
      {
        "keyList": {
          "keys": [
            {
              "key": "validator 1 public key"
            },
            {
              "key": "validator 2 public key"
            },
            {
              "key": "validator 3 public key"
            }
          ],
          "threshold": 2
        }
      }
    ],
    "threshold" : 1
  },
  "initialBalance": 100
}' http://localhost:8082/v1/admin/auctionaccount
```

## Creating an auction

be sure the replace `{{tokenId}}`, `{{accountId}}` in the json below with the values you obtained earlier, you may also set different values for:

* `reserve` in tinybars
* `minimumbid` in tinybars
* `endtimestamp` will default to 2 days in the future if not set, otherwise specify the date and time you wish the auction to end in seconds since epoch
* `winnercanbid` whether the highest bidder is allowed to place a higher bid
* `title` and `description` for the auction (rendered in the UI)

```shell script
curl -H "Content-Type: application/json" -X POST -d '
{
  "tokenid": "{{tokenId}}", 
  "auctionaccountid": "{{accountId}}", 
  "reserve": "", 
  "minimumbid": "1000000", 
  "endtimestamp": "", 
  "winnercanbid": true,
  "title": "Auction title",
  "description": "Auction description"
}' http://localhost:8082/v1/admin/auction
```

_Note: the minimum bid and reserve are expressed in `tinybars`_

This will submit a HCS message on the application network's topic id so that all participants are aware of the auction.
