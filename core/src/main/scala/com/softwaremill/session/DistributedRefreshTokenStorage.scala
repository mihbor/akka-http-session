package com.softwaremill.session

import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.ddata.{Replicator, ReplicatorSettings}

trait DistributedRefreshTokenStorage[T] extends RefreshTokenStorage[T]{
  def storeName: String
  implicit val system: ActorSystem
  private val replicator: ActorRef = system.actorOf(
    Replicator.props(
      ReplicatorSettings(system.settings.config.getConfig("akka.cluster.distributed-data"))
      .withRole(storeName)
    )
  )
}
