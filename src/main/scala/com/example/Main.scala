package com.example

import zio._
import zio.console._
import zio.random._

object UseLRUCacheWithOneFiber extends App {
  def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    (for {
      lruCache <- LRUCache.make[Int, Int](2)
      _        <- put(lruCache, 1, 1)
      _        <- put(lruCache, 2, 2)
      _        <- get(lruCache, 1)
      _        <- put(lruCache, 3, 3)
      _        <- get(lruCache, 2)
      _        <- put(lruCache, 4, 4)
      _        <- get(lruCache, 1)
      _        <- get(lruCache, 3)
      _        <- get(lruCache, 4)
    } yield 0).catchAll(ex => putStrLn(ex.getMessage) *> ZIO.succeed(1))

  private def get[K, V](lruCache: LRUCache[K, V], key: K): ZIO[Console, Nothing, Unit] =
    (for {
      _ <- putStrLn(s"Getting key: $key")
      v <- lruCache.get(key)
      _ <- putStrLn(s"Obtained value: $v")
    } yield ()).catchAll(ex => putStrLn(ex.getMessage))

  private def put[K, V](lruCache: LRUCache[K, V], key: K, value: V): ZIO[Console, Nothing, Unit] =
    putStrLn(s"Putting ($key, $value)") *> lruCache.put(key, value)
}

object UseLRUCacheWithMultipleFibers extends App {
  def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    (for {
      lruCache       <- LRUCache.make[Int, Int](3)
      fiberReporter  <- reporter(lruCache).forever.fork
      fiberProducers <- ZIO.forkAll(ZIO.replicate(100)(producer(lruCache).forever))
      fiberConsumers <- ZIO.forkAll(ZIO.replicate(100)(consumer(lruCache).forever))
      _              <- getStrLn.orDie *> (fiberReporter <*> fiberProducers <*> fiberConsumers).interrupt
    } yield 0).catchAll(ex => putStrLn(ex.getMessage) *> ZIO.succeed(1))

  def producer(lruCache: LRUCache[Int, Int]): URIO[Console with Random, Unit] =
    for {
      number <- nextInt(100)
      _      <- putStrLn(s"Producing ($number, $number)")
      _      <- lruCache.put(number, number)
    } yield ()

  def consumer(lruCache: LRUCache[Int, Int]): URIO[Console with Random, Unit] =
    (for {
      key   <- nextInt(100)
      _     <- putStrLn(s"Consuming key: $key")
      value <- lruCache.get(key)
      _     <- putStrLn(s"Consumed value: $value")
    } yield ()).catchAll(ex => putStrLn(ex.getMessage))

  def reporter(lruCache: LRUCache[Int, Int]): ZIO[Console, NoSuchElementException, Unit] =
    for {
      (items, optionStart, optionEnd) <- lruCache.getStatus
      _                               <- putStrLn(s"Items: $items, Start: $optionStart, End: $optionEnd")
    } yield ()
}

object UseConcurrentLRUCacheWithMultipleFibers extends App {
  def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    (for {
      concurrentLruCache <- ConcurrentLRUCache.make[Int, Int](3)
      fiberReporter      <- reporter(concurrentLruCache).forever.fork
      fiberProducers     <- ZIO.forkAll(ZIO.replicate(100)(producer(concurrentLruCache).forever))
      fiberConsumers     <- ZIO.forkAll(ZIO.replicate(100)(consumer(concurrentLruCache).forever))
      _                  <- getStrLn.orDie *> (fiberReporter <*> fiberProducers <*> fiberConsumers).interrupt
    } yield 0).catchAll(ex => putStrLn(ex.getMessage) *> ZIO.succeed(1))

  def producer(concurrentLruCache: ConcurrentLRUCache[Int, Int]): URIO[Console with Random, Unit] =
    for {
      number <- nextInt(100)
      _      <- putStrLn(s"Producing ($number, $number)")
      _      <- concurrentLruCache.put(number, number)
    } yield ()

  def consumer(concurrentLruCache: ConcurrentLRUCache[Int, Int]): URIO[Console with Random, Unit] =
    (for {
      key   <- nextInt(100)
      _     <- putStrLn(s"Consuming key: $key")
      value <- concurrentLruCache.get(key)
      _     <- putStrLn(s"Consumed value: $value")
    } yield ()).catchAll(ex => putStrLn(ex.getMessage))

  def reporter(concurrentLruCache: ConcurrentLRUCache[Int, Int]): ZIO[Console, NoSuchElementException, Unit] =
    for {
      (items, optionStart, optionEnd) <- concurrentLruCache.getStatus
      _                               <- putStrLn(s"Items: $items, Start: $optionStart, End: $optionEnd")
    } yield ()
}
