package com.example

import zio._
import zio.console._
import zio.random._
import cache._

object UseLRUCacheWithOneFiber extends App {
  def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
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
    } yield 0)
      .provideCustomLayer(ZLayer.succeed(2) >>> IntLRUCacheEnv.Service.zioRefImpl)
      .catchAll(ex => putStrLn(ex.getMessage) *> ZIO.succeed(1))

  private def get(key: Int): URIO[Console with IntLRUCacheEnv, Unit] =
    (for {
      _ <- putStrLn(s"Getting key: $key")
      v <- getInt(key)
      _ <- putStrLn(s"Obtained value: $v")
    } yield ()).catchAll(ex => putStrLn(ex.getMessage))

  private def put(key: Int, value: Int): URIO[Console with IntLRUCacheEnv, Unit] =
    putStrLn(s"Putting ($key, $value)") *> putInt(key, value)
}

object UseLRUCacheWithMultipleFibers extends App {
  def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    (for {
      fiberReporter  <- reporter.forever.fork
      fiberProducers <- ZIO.forkAll(ZIO.replicate(100)(producer.forever))
      fiberConsumers <- ZIO.forkAll(ZIO.replicate(100)(consumer.forever))
      _              <- getStrLn.orDie *> (fiberReporter <*> fiberProducers <*> fiberConsumers).interrupt
    } yield 0)
      .provideCustomLayer(ZLayer.succeed(3) >>> IntLRUCacheEnv.Service.zioRefImpl)
      .catchAll(ex => putStrLn(ex.getMessage) *> ZIO.succeed(1))

  val producer: URIO[Console with Random with IntLRUCacheEnv, Unit] =
    for {
      number <- nextInt(100)
      _      <- putStrLn(s"Producing ($number, $number)")
      _      <- putInt(number, number)
    } yield ()

  val consumer: URIO[Console with Random with IntLRUCacheEnv, Unit] =
    (for {
      key   <- nextInt(100)
      _     <- putStrLn(s"Consuming key: $key")
      value <- getInt(key)
      _     <- putStrLn(s"Consumed value: $value")
    } yield ()).catchAll(ex => putStrLn(ex.getMessage))

  val reporter: ZIO[Console with IntLRUCacheEnv, NoSuchElementException, Unit] =
    for {
      (items, optionStart, optionEnd) <- getCacheStatus
      _                               <- putStrLn(s"Items: $items, Start: $optionStart, End: $optionEnd")
    } yield ()
}

object UseConcurrentLRUCacheWithMultipleFibers extends App {
  def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    (for {
      fiberReporter  <- reporter.forever.fork
      fiberProducers <- ZIO.forkAll(ZIO.replicate(100)(producer.forever))
      fiberConsumers <- ZIO.forkAll(ZIO.replicate(100)(consumer.forever))
      _              <- getStrLn.orDie *> (fiberReporter <*> fiberProducers <*> fiberConsumers).interrupt
    } yield 0)
      .provideCustomLayer(ZLayer.succeed(3) >>> IntLRUCacheEnv.Service.zioStmImpl)
      .catchAll(ex => putStrLn(ex.getMessage) *> ZIO.succeed(1))

  val producer: URIO[Console with Random with IntLRUCacheEnv, Unit] =
    for {
      number <- nextInt(100)
      _      <- putStrLn(s"Producing ($number, $number)")
      _      <- putInt(number, number)
    } yield ()

  val consumer: URIO[Console with Random with IntLRUCacheEnv, Unit] =
    (for {
      key   <- nextInt(100)
      _     <- putStrLn(s"Consuming key: $key")
      value <- getInt(key)
      _     <- putStrLn(s"Consumed value: $value")
    } yield ()).catchAll(ex => putStrLn(ex.getMessage))

  val reporter: ZIO[Console with IntLRUCacheEnv, NoSuchElementException, Unit] =
    for {
      (items, optionStart, optionEnd) <- getCacheStatus
      _                               <- putStrLn(s"Items: $items, Start: $optionStart, End: $optionEnd")
    } yield ()
}
