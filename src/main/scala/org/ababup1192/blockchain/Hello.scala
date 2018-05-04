package org.ababup1192.blockchain

import akka.actor.{Actor, ActorSystem, Props}

case object NewBlock

case object NewTransaction

trait BlockChainElement

case object Block extends BlockChainElement

class BlockChain extends Actor {
  var chain = List()
  var currentTransactions = List()

  def receive = {
    case NewBlock => println("NewBlock")
    case NewTransaction => println("NewTransaction")
  }
}

object BlockChain {

  def hash(block: BlockChainElement) = ???

  def lastBlock(): BlockChainElement = Block
}

object Main extends App {
  val system = ActorSystem("BlockChainSystem")
  val blockChain = system.actorOf(Props[BlockChain], name = "blockChain")

  blockChain ! NewBlock
  blockChain ! NewTransaction
}
