package com.example

import zio.ZIO
import zio.console._
import zio.test.Assertion._
import zio.test.environment._
import zio.test._

object ConcurrentLRUCacheTest extends DefaultRunnableSpec {
  def spec = suite("ConcurrentLRUCache")(
    testM("can't be created with non-positive capacity") {
      assertM(ConcurrentLRUCache.make[Int, Int](0).run)(
        fails(hasMessage(equalTo("Capacity must be a positive number!")))
      )
    },
    testM("works as expected") {
      val expectedOutput = Vector(
        "Putting (1, 1)\n",
        "Putting (2, 2)\n",
        "Getting key: 1\n",
        "Obtained value: 1\n",
        "Putting (3, 3)\n",
        "Getting key: 2\n",
        "Key does not exist: 2\n",
        "Putting (4, 4)\n",
        "Getting key: 1\n",
        "Key does not exist: 1\n",
        "Getting key: 3\n",
        "Obtained value: 3\n",
        "Getting key: 4\n",
        "Obtained value: 4\n"
      )
      for {
        lruCache <- ConcurrentLRUCache.make[Int, Int](2)
        _        <- put(lruCache, 1, 1)
        _        <- put(lruCache, 2, 2)
        _        <- get(lruCache, 1)
        _        <- put(lruCache, 3, 3)
        _        <- get(lruCache, 2)
        _        <- put(lruCache, 4, 4)
        _        <- get(lruCache, 1)
        _        <- get(lruCache, 3)
        _        <- get(lruCache, 4)
        output   <- TestConsole.output
      } yield {
        assert(output)(equalTo(expectedOutput))
      }
    }
  )

  private def get[K, V](concurrentLruCache: ConcurrentLRUCache[K, V], key: K): ZIO[Console, Nothing, Unit] =
    (for {
      _ <- putStrLn(s"Getting key: $key")
      v <- concurrentLruCache.get(key)
      _ <- putStrLn(s"Obtained value: $v")
    } yield ()).catchAll(ex => putStrLn(ex.getMessage))

  private def put[K, V](concurrentLruCache: ConcurrentLRUCache[K, V], key: K, value: V): ZIO[Console, Nothing, Unit] =
    putStrLn(s"Putting ($key, $value)") *> concurrentLruCache.put(key, value)
}
