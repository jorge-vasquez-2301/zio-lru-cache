package com.example.cache

import zio._
import zio.stm._

final class ConcurrentLRUCache[K, V] private (
  private val capacity: Int,
  private val items: TMap[K, CacheItem[K, V]],
  private val startRef: TRef[Option[K]],
  private val endRef: TRef[Option[K]]
) { self =>

  def get(key: K): IO[NoSuchElementException, V] =
    (for {
      optionItem <- self.items.get(key)
      item       <- STM.fromOption(optionItem).mapError(_ => new NoSuchElementException(s"Key does not exist: $key"))
      _          <- removeKeyFromList(key) *> addKeyToStartOfList(key)
    } yield item.value).commitEither.refineToOrDie[NoSuchElementException]

  def put(key: K, value: V): UIO[Unit] =
    (for {
      optionStart <- self.startRef.get
      optionEnd   <- self.endRef.get
      _           <- STM.ifM(self.items.contains(key))(updateItem(key, value), addNewItem(key, value, optionStart, optionEnd))
    } yield ()).commitEither.orDie

  val getStatus: UIO[(Map[K, CacheItem[K, V]], Option[K], Option[K])] =
    (for {
      items       <- (items.keys zipWith items.values) { case (keys, values) => (keys zip values).toMap }
      optionStart <- startRef.get
      optionEnd   <- endRef.get
    } yield (items, optionStart, optionEnd)).commit

  private def updateItem(key: K, value: V): STM[Error, Unit] =
    removeKeyFromList(key) *>
      self.items.put(key, CacheItem(value, None, None)) *>
      addKeyToStartOfList(key)

  private def addNewItem(key: K, value: V, optionStart: Option[K], optionEnd: Option[K]): STM[Error, Unit] = {
    val newCacheItem = CacheItem[K, V](value, None, None)
    STM.ifM(self.items.keys.map(_.length < self.capacity))(
      self.items.put(key, newCacheItem) *> addKeyToStartOfList(key),
      replaceEndCacheItem(key, newCacheItem)
    )
  }

  private def replaceEndCacheItem(key: K, newCacheItem: CacheItem[K, V]): STM[Error, Unit] =
    endRef.get.flatMap {
      case Some(end) =>
        removeKeyFromList(end) *> self.items.delete(end) *> self.items.put(key, newCacheItem) *> addKeyToStartOfList(
          key
        )
      case None => STM.fail(new Error(s"End is not defined!"))
    }

  private def addKeyToStartOfList(key: K): STM[Error, Unit] =
    for {
      oldOptionStart <- self.startRef.get
      _ <- getExistingCacheItem(key).flatMap { cacheItem =>
            self.items.put(key, cacheItem.copy(left = None, right = oldOptionStart))
          }
      _ <- oldOptionStart match {
            case Some(oldStart) =>
              getExistingCacheItem(oldStart).flatMap { oldStartCacheItem =>
                self.items.put(oldStart, oldStartCacheItem.copy(left = Some(key)))
              }
            case None => STM.unit
          }
      _ <- self.startRef.set(Some(key))
      _ <- self.endRef.updateSome { case None => Some(key) }
    } yield ()

  private def removeKeyFromList(key: K): STM[Error, Unit] =
    for {
      cacheItem      <- getExistingCacheItem(key)
      optionLeftKey  = cacheItem.left
      optionRightKey = cacheItem.right
      _ <- (optionLeftKey, optionRightKey) match {
            case (Some(l), Some(r)) =>
              updateLeftAndRightCacheItems(l, r)
            case (Some(l), None) =>
              setNewEnd(l)
            case (None, Some(r)) =>
              setNewStart(r)
            case (None, None) =>
              clearStartAndEnd
          }
    } yield ()

  private def updateLeftAndRightCacheItems(l: K, r: K): STM[Error, Unit] =
    for {
      leftCacheItem  <- getExistingCacheItem(l)
      rightCacheItem <- getExistingCacheItem(r)
      _              <- self.items.put(l, leftCacheItem.copy(right = Some(r)))
      _              <- self.items.put(r, rightCacheItem.copy(left = Some(l)))
    } yield ()

  private def setNewEnd(newEnd: K): STM[Error, Unit] =
    for {
      cacheItem <- getExistingCacheItem(newEnd)
      _         <- self.items.put(newEnd, cacheItem.copy(right = None)) *> self.endRef.set(Some(newEnd))
    } yield ()

  private def setNewStart(newStart: K): STM[Error, Unit] =
    for {
      cacheItem <- getExistingCacheItem(newStart)
      _         <- self.items.put(newStart, cacheItem.copy(left = None)) *> self.startRef.set(Some(newStart))
    } yield ()

  private val clearStartAndEnd: STM[Nothing, Unit] = (self.startRef.set(None) *> self.endRef.set(None))

  private def getExistingCacheItem(key: K): STM[Error, CacheItem[K, V]] =
    STM.require(new Error(s"Key does not exist: $key"))(self.items.get(key))
}

object ConcurrentLRUCache {
  def make[K, V](capacity: Int): IO[IllegalArgumentException, ConcurrentLRUCache[K, V]] =
    if (capacity > 0) {
      (for {
        itemsRef <- TMap.empty[K, CacheItem[K, V]]
        startRef <- TRef.make(Option.empty[K])
        endRef   <- TRef.make(Option.empty[K])
      } yield new ConcurrentLRUCache[K, V](capacity, itemsRef, startRef, endRef)).commit
    } else {
      ZIO.fail(new IllegalArgumentException("Capacity must be a positive number!"))
    }
}
