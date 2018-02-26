package com.softwaremill.session

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.ddata.{DistributedData, LWWMap, LWWMapKey}
import akka.cluster.ddata.Replicator._
import akka.pattern.ask

import scala.concurrent.duration.FiniteDuration

trait DistributedRefreshTokenStorage[T] extends RefreshTokenStorage[T] {
  val system: ActorSystem
  implicit val ec = system.dispatcher
  val replicator = DistributedData.get(system).replicator
  implicit val node = Cluster(system)
  implicit val timeout = FiniteDuration(1, TimeUnit.SECONDS)

  case class Store(session: T, tokenHash: String, expires: Long)

  def mapSelector(selector: String) = (selector.hashCode % 1000).toString

  override def lookup(selector: String) = {
    val selector: String = mapSelector(selector)
    val key = LWWMapKey[String, Map[String, Store]](selector)

    ask(replicator, Get(key, ReadMajority(timeout)))(timeout).map {
      case g @ GetSuccess(LWWMapKey(selector), _) =>
        val r = g.get(key)
          .get(selector)
          .flatMap(map => map.get(selector))
          .map(s => RefreshTokenLookupResult[T](s.tokenHash, s.expires, () => s.session))
        log(s"Looking up token for selector: $selector, found: ${r.isDefined}")
        r
      case NotFound =>
        None
    }
  }

  override def store(data: RefreshTokenData[T]) = {
    log(s"Storing token for selector: ${data.selector}, user: ${data.forSession}, " +
      s"expires: ${data.expires}, now: ${System.currentTimeMillis()}")

    val selector: String = mapSelector(selector)
    val key = LWWMapKey[String, Map[String, Store]](selector)

    ask(replicator,
      Update(key, LWWMap.empty[String, Map[String, Store]], WriteMajority(timeout))(lwwmap =>
        lwwmap.get(selector) match {
          case Some(map) => lwwmap + (selector, map + (selector -> Store(data.forSession, data.tokenHash, data.expires)))
          case None => lwwmap + (selector, Map(selector -> Store(data.forSession, data.tokenHash, data.expires)))
        }
      )
    )(timeout).map {
      case UpdateSuccess => Unit
    }
  }

  override def remove(s: String) = {
    log(s"Removing token for selector" + s)

    val selector = mapSelector(s)
    val key = LWWMapKey[String, Map[String, Store]](selector)

    ask(replicator,
      Update(key, LWWMap.empty[String, Map[String, Store]], WriteMajority(timeout))(lwwmap =>
        lwwmap.get(selector) match {
          case Some(map) => lwwmap + (selector, map - selector)
        }
      )
    )(timeout).map {
      case UpdateSuccess => Unit
    }
  }

  def log(msg: String): Unit
}
