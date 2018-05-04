package org.ababup1192.blockchain

import java.security.MessageDigest

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

case class NewBlock(proof: Int, previousHash: Option[String])

case class NewTransaction(sender: ActorRef, recipient: ActorRef, amount: Int)

case class Transaction(sender: ActorRef, recipient: ActorRef, amount: Int)

case class Block(index: Int, timestamp: Long, transactions: Seq[Transaction], proof: Int, previousHash: Option[String])


class BlockChain extends Actor {
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
        Some(previousHash.getOrElse(BlockChain.hash(lastBlock())))
      )

      println(block)
      currentTransactions = Seq()

      chain = chain :+ block
      block

    case NewTransaction(s, r, amount) =>
      currentTransactions = currentTransactions :+ Transaction(s, r, amount)
      lastBlock().index + 1
  }

  private def lastBlock(): Block = chain.last
}

object BlockChain {

  def hash(block: Block): String = sha256Hash(block.toString)

  private def sha256Hash(text: String) : String =
    String.format("%064x", new java.math.BigInteger(1, java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes("UTF-8"))))

}

object Main extends App {
  val system = ActorSystem("BlockChainSystem")
  val blockChain = system.actorOf(Props[BlockChain], name = "blockChain")

}
