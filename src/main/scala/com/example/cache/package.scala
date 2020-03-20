package com.example

import zio._

package object cache {
  type IntLRUCacheEnv = Has[IntLRUCacheEnv.Service]

  object IntLRUCacheEnv {

    trait Service {
      def getInt(key: Int): IO[NoSuchElementException, Int]
      def putInt(key: Int, value: Int): UIO[Unit]
      val getCacheStatus: UIO[(Map[Int, CacheItem[Int, Int]], Option[Int], Option[Int])]
    }

    object Service {
      val zioRefImpl: ZLayer[Has[Int], IllegalArgumentException, IntLRUCacheEnv] =
        ZLayer.fromFunctionM { hasInt: Has[Int] =>
          LRUCache.make[Int, Int](hasInt.get).map { lruCache =>
            new Service {
              override def getInt(key: Int): IO[NoSuchElementException, Int] = lruCache.get(key)

              override def putInt(key: Int, value: Int): UIO[Unit] = lruCache.put(key, value)

              override val getCacheStatus: UIO[(Map[Int, CacheItem[Int, Int]], Option[Int], Option[Int])] =
                lruCache.getStatus
            }
          }
        }

      val zioStmImpl: ZLayer[Has[Int], IllegalArgumentException, IntLRUCacheEnv] =
        ZLayer.fromFunctionM { hasInt: Has[Int] =>
          ConcurrentLRUCache.make[Int, Int](hasInt.get).map { concurrentLruCache =>
            new Service {
              override def getInt(key: Int): IO[NoSuchElementException, Int] = concurrentLruCache.get(key)

              override def putInt(key: Int, value: Int): UIO[Unit] = concurrentLruCache.put(key, value)

              override val getCacheStatus: UIO[(Map[Int, CacheItem[Int, Int]], Option[Int], Option[Int])] =
                concurrentLruCache.getStatus
            }
          }
        }
    }
  }

  def getInt(key: Int): ZIO[IntLRUCacheEnv, NoSuchElementException, Int] = ZIO.accessM(_.get.getInt(key))

  def putInt(key: Int, value: Int): ZIO[IntLRUCacheEnv, Nothing, Unit] = ZIO.accessM(_.get.putInt(key, value))

  val getCacheStatus: ZIO[IntLRUCacheEnv, Nothing, (Map[Int, CacheItem[Int, Int]], Option[Int], Option[Int])] =
    ZIO.accessM(_.get.getCacheStatus)
}
