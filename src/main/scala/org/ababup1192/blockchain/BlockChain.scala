package org.ababup1192.blockchain

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Inbox, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.annotation.tailrec
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

case class Transaction(sender: String, recipient: String, amount: Int)

case class Block(index: Int, timestamp: Long, transactions: Seq[Transaction], proof: Int, previousHash: Option[String])

class BlockChain extends Actor with ActorLogging {

  import BlockChain._

  var chain: Seq[Block] = Seq()
  var currentTransactions: Seq[Transaction] = Seq()

  self ! NewBlock(100, Some("1"))

  def receive = {
    case NewBlock(proof, previousHash) =>
      val block = Block(
        chain.length + 1,
        System.currentTimeMillis(),
        currentTransactions,
        proof,
        Some(previousHash.getOrElse(hash(lastBlock())))
      )

      currentTransactions = Seq()

      chain = chain :+ block
      sender ! block

    case NewTransaction(s, r, amount) =>

      currentTransactions = currentTransactions :+ Transaction(s, r, amount)

      sender ! lastBlock().index + 1


    case ProofOfWork(lastProof) =>
      log.info("proof of work")

      @tailrec
      def loop(proof: Int = 0): Int = {
        if (validProof(lastProof, proof)) proof
        else loop(proof + 1)
      }

      sender ! loop()

    case Chain =>
      sender ! chain

    case LastBlock =>
      sender ! lastBlock()
  }

  private def lastBlock(): Block = chain.last
}

object BlockChain {

  case class NewBlock(proof: Int, previousHash: Option[String])

  case class NewTransaction(sender: String, recipient: String, amount: Int)

  case object LastBlock

  case class ProofOfWork(lastProof: Int)

  case object Chain

  def hash(block: Block): String = sha256Hash(block.toString)

  def validProof(lastProof: Int, proof: Int): Boolean = {
    val guess = s"$lastProof$proof"
    val guessHash = sha256Hash(guess)

    guessHash.substring(0, 4) == "0000"
  }


  private def sha256Hash(text: String): String =
    String.format("%064x", new java.math.BigInteger(1, java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes("UTF-8"))))
}


class BlockChainClient extends Actor with ActorLogging {

  import BlockChainClient._

  implicit val timeout: Timeout = Timeout(5000 second)
  val nodeIdentifire = java.util.UUID.randomUUID.toString.replace("-", "")


  def receive = {
    case NewTransaction(blockChain, s, r, amount) =>
      sender ! (blockChain ? BlockChain.NewTransaction(s, r, amount)).mapTo[Int]
        .map(index => NewTransactionView(s"トランザクションはブロック $index に追加されました"))

    case Mine(blockChain) =>
      sender ! (for {
        lastBlock <- (blockChain ? BlockChain.LastBlock).mapTo[Block]
        lastProof = lastBlock.proof
        proof <- (blockChain ? BlockChain.ProofOfWork(lastProof)).mapTo[Int]
        _ <- blockChain ? BlockChain.NewTransaction("0", nodeIdentifire, 1)
        block <- (blockChain ? BlockChain.NewBlock(proof, None)).mapTo[Block]
      } yield MineView("新しいブロックを採掘しました", block.index, block.transactions, proof, block.previousHash))

    case Chain(blockChain) =>
      sender ! (blockChain ? BlockChain.Chain).mapTo[Seq[Block]]
        .map(chain => ChainView(chain, chain.length))
  }
}

object BlockChainClient {

  case class NewTransaction(blockChain: ActorRef, sender: String, recipient: String, amount: Int)

  case class Mine(blockChain: ActorRef)

  case class Chain(blockChain: ActorRef)

  case class NewTransactionView(message: String)

  case class MineView(message: String, index: Int, transactions: Seq[Transaction], proof: Int, previousHash: Option[String])

  case class ChainView(chain: Seq[Block], length: Int)

}

object Main extends App {
  val system = ActorSystem("BlockChainSystem")
  val blockChain = system.actorOf(Props[BlockChain], "blockChain")
  val blockChainClient = system.actorOf(Props[BlockChainClient], "blockChainClient")

  val inbox = Inbox.create(system)

  inbox.send(blockChainClient, BlockChainClient.Mine(blockChain))

  inbox.receive(5 seconds).asInstanceOf[Future[BlockChainClient.MineView]].foreach(
    mineView => println(mineView)
  )

  Thread.sleep(500)

  inbox.send(
    blockChainClient,
    BlockChainClient.NewTransaction(blockChain, "d4ee26eee15148ee92c6cd394edd974e", "someone-other-address", 5)
  )

  inbox.receive(5 seconds).asInstanceOf[Future[BlockChainClient.NewTransactionView]].foreach(
    newTransactionView => println(newTransactionView)
  )

  Thread.sleep(500)

  inbox.send(blockChainClient, BlockChainClient.Chain(blockChain))

  inbox.receive(5 seconds).asInstanceOf[Future[BlockChainClient.ChainView]].foreach(
    chainView => println(chainView)
  )

}
