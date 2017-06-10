package com.softwaremill.session

import org.scalatest.{AsyncFlatSpec, Matchers}

class InMemoryRefreshTokenStorageTest extends AsyncFlatSpec with Matchers {
  val selector = "selector"

  val refreshTokenData = new RefreshTokenData[String](
    forSession = "session",
    selector = selector,
    tokenHash = Crypto.hash_SHA256("token1"),
    expires = System.currentTimeMillis() + 1000L
  )

  def storage = new InMemoryRefreshTokenStorage[String] {
    override def log(msg: String) = println(msg)
  }

  "InMemoryRefreshTokenStorage" should "be initially empty" in {
    storage.store.isEmpty should be (true)
  }

  "InMemoryRefreshTokenStorage" should "lookup what was put in" in {
    val store = storage
    store.store(refreshTokenData).flatMap(ok => store.lookup(selector)).map(
      result => {
        result should not be (None)
        result.get.expires should be(refreshTokenData.expires)
        result.get.tokenHash should be (refreshTokenData.tokenHash)
      }
    )
  }

  "InMemoryRefreshTokenStorage" should "not share tokens between instances" in {
    val store1 = storage
    val store2 = storage
    store1.store(refreshTokenData).flatMap(ok => store2.lookup(selector)).map(
      result => result should be (None)
    )
  }

  "InMemoryRefreshTokenStorage" should "not find remove tokens" in {
    val store = storage
    store.store(refreshTokenData).flatMap(ok => store.lookup(selector)).flatMap(
      result => {
        result should not be (None)
        store.remove(selector).flatMap(ok => store.lookup(selector)).map(
          result => result should be (None)
        )
      }
    )
  }

  "InMemoryRefreshTokenStorage" should "not remove tokens from another instance" in {
    val store1 = storage
    val store2 = storage
    store1.store(refreshTokenData).flatMap(ok => store2.store(refreshTokenData)).flatMap(ok => store1.lookup(selector)).flatMap(
      result => {
        result should not be (None)
        store2.lookup(selector).flatMap(
          result => {
            result should not be (None)
            store1.remove(selector).flatMap(ok => store1.lookup(selector)).flatMap(
              result => {
                result should be(None)
                store2.lookup(selector).flatMap(
                  result => {
                    result should not be (None)
                    result.get.expires should be(refreshTokenData.expires)
                    result.get.tokenHash should be(refreshTokenData.tokenHash)
                  }
                )
              }
            )
          }
        )
      }
    )
  }
}
