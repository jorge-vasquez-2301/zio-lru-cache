package com.example.cache

import zio._
import zio.test._

object LRUCacheSTMSpec extends ZIOSpecDefault {
  def spec =
    suite("LRUCacheSTM")(
      test("can't be created with non-positive capacity") {
        for {
          result <- LRUCache.put(1, 1).provideLayer(LRUCacheSTM.layer(-2)).absorb.either
        } yield assertTrue(result.left.map(_.getMessage) == Left("Capacity must be a positive number!"))
      },
      test("works as expected") {
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
          _      <- put(1, 1)
          _      <- put(2, 2)
          _      <- get(1)
          _      <- put(3, 3)
          _      <- get(2)
          _      <- put(4, 4)
          _      <- get(1)
          _      <- get(3)
          _      <- get(4)
          output <- TestConsole.output
        } yield assertTrue(output == expectedOutput)
      }.provideLayer(LRUCacheSTM.layer[Int, Int](2))
    ) @@ TestAspect.silent

  private def get(key: Int): URIO[LRUCache[Int, Int], Unit] =
    (for {
      v <- Console.printLine(s"Getting key: $key") *> LRUCache.get[Int, Int](key)
      _ <- Console.printLine(s"Obtained value: $v")
    } yield ()).catchAll(ex => Console.printLine(ex.getMessage).orDie)

  private def put(key: Int, value: Int): URIO[LRUCache[Int, Int], Unit] =
    Console.printLine(s"Putting ($key, $value)").orDie *> LRUCache.put(key, value)
}
