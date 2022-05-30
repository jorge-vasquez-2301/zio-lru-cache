package com.example

import com.example.cache._
import zio._

object UseLRUCacheRefWithOneFiber extends ZIOAppDefault {
  val run: UIO[Int] =
    (for {
      _ <- put(1, 1)
      _ <- put(2, 2)
      _ <- get(1)
      _ <- put(3, 3)
      _ <- get(2)
      _ <- put(4, 4)
      _ <- get(1)
      _ <- get(3)
      _ <- get(4)
    } yield 0).provideLayer(LRUCacheRef.layer(capacity = 2))

  private def get(key: Int): URIO[LRUCache[Int, Int], Unit] =
    (for {
      v <- Console.printLine(s"Getting key: $key") *> LRUCache.get[Int, Int](key)
      _ <- Console.printLine(s"Obtained value: $v")
    } yield ()).catchAll(ex => Console.printLine(ex.getMessage).orDie)

  private def put(key: Int, value: Int): URIO[LRUCache[Int, Int], Unit] =
    Console.printLine(s"Putting ($key, $value)").orDie *> LRUCache.put(key, value)
}

object UseLRUCacheRefWithMultipleFibers extends ZIOAppDefault {
  lazy val run =
    (for {
      fiberReporter  <- reporter.forever.fork
      fiberProducers <- ZIO.forkAll(ZIO.replicate(100)(producer.forever))
      fiberConsumers <- ZIO.forkAll(ZIO.replicate(100)(consumer.forever))
      _              <- Console.readLine.orDie *> (fiberReporter <*> fiberProducers <*> fiberConsumers).interrupt
    } yield 0).provideLayer(LRUCacheRef.layer(capacity = 3))

  lazy val producer: URIO[LRUCache[Int, Int], Unit] =
    for {
      number <- Random.nextIntBounded(100)
      _      <- Console.printLine(s"Producing ($number, $number)").orDie *> LRUCache.put(number, number)
    } yield ()

  lazy val consumer: URIO[LRUCache[Int, Int], Unit] =
    (for {
      key   <- Random.nextIntBounded(100)
      value <- Console.printLine(s"Consuming key: $key") *> LRUCache.get[Int, Int](key)
      _     <- Console.printLine(s"Consumed value: $value")
    } yield ()).catchAll(ex => Console.printLine(ex.getMessage).orDie)

  lazy val reporter: ZIO[LRUCache[Int, Int], NoSuchElementException, Unit] =
    for {
      status                          <- LRUCache.getStatus[Int, Int]
      (items, optionStart, optionEnd) = status
      _                               <- Console.printLine(s"Items: $items, Start: $optionStart, End: $optionEnd").orDie
    } yield ()
}

object UseLRUCacheSTMWithMultipleFibers extends ZIOAppDefault {
  lazy val run =
    (for {
      fiberReporter  <- reporter.forever.fork
      fiberProducers <- ZIO.forkAll(ZIO.replicate(100)(producer.forever))
      fiberConsumers <- ZIO.forkAll(ZIO.replicate(100)(consumer.forever))
      _              <- Console.readLine.orDie *> (fiberReporter <*> fiberProducers <*> fiberConsumers).interrupt
    } yield 0).provideLayer(LRUCacheSTM.layer(capacity = 3))

  lazy val producer: URIO[LRUCache[Int, Int], Unit] =
    for {
      number <- Random.nextIntBounded(100)
      _      <- Console.printLine(s"Producing ($number, $number)").orDie *> LRUCache.put(number, number)
    } yield ()

  lazy val consumer: URIO[LRUCache[Int, Int], Unit] =
    (for {
      key   <- Random.nextIntBounded(100)
      value <- Console.printLine(s"Consuming key: $key") *> LRUCache.get[Int, Int](key)
      _     <- Console.printLine(s"Consumed value: $value")
    } yield ()).catchAll(ex => Console.printLine(ex.getMessage).orDie)

  lazy val reporter: ZIO[LRUCache[Int, Int], NoSuchElementException, Unit] =
    for {
      status                          <- LRUCache.getStatus[Int, Int]
      (items, optionStart, optionEnd) = status
      _                               <- Console.printLine(s"Items: $items, Start: $optionStart, End: $optionEnd").orDie
    } yield ()
}
