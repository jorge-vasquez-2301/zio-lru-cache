package com.example

import zio._

class LRUCache[K, V] private (
  private val capacity: Int,
  private val itemsRef: Ref[Map[K, CacheItem[K, V]]],
  private val startRef: Ref[Option[K]],
  private val endRef: Ref[Option[K]]
) { self =>

  def get(key: K): IO[NoSuchElementException, V] =
    (for {
      items <- self.itemsRef.get
      item  <- ZIO.fromOption(items.get(key)).mapError(_ => new NoSuchElementException(s"Key does not exist: $key"))
      _     <- removeKeyFromList(key) *> addKeyToStartOfList(key)
    } yield item.value).refineToOrDie[NoSuchElementException]

  def put(key: K, value: V): UIO[Unit] =
    (for {
      optionStart <- self.startRef.get
      optionEnd   <- self.endRef.get
      _ <- ZIO.ifM(self.itemsRef.get.map(_.contains(key)))(
            updateItem(key, value),
            addNewItem(key, value, optionStart, optionEnd)
          )
    } yield ()).orDie

  val getStatus: UIO[(Map[K, CacheItem[K, V]], Option[K], Option[K])] =
    for {
      items       <- itemsRef.get
      optionStart <- startRef.get
      optionEnd   <- endRef.get
    } yield (items, optionStart, optionEnd)

  private def updateItem(key: K, value: V): IO[Error, Unit] =
    removeKeyFromList(key) *>
      self.itemsRef.update(_.updated(key, CacheItem(value, None, None))) *>
      addKeyToStartOfList(key)

  private def addNewItem(key: K, value: V, optionStart: Option[K], optionEnd: Option[K]): IO[Error, Unit] = {
    val newCacheItem = CacheItem[K, V](value, None, None)
    ZIO.ifM(self.itemsRef.get.map(_.size < self.capacity))(
      self.itemsRef.update(_ + (key -> newCacheItem)) *> addKeyToStartOfList(key),
      replaceEndCacheItem(key, newCacheItem)
    )
  }

  private def replaceEndCacheItem(key: K, newCacheItem: CacheItem[K, V]): IO[Error, Unit] =
    endRef.get.flatMap {
      case Some(end) =>
        removeKeyFromList(end) *> self.itemsRef.update(_ - end + (key -> newCacheItem)) *> addKeyToStartOfList(key)
      case None => ZIO.fail(new Error(s"End is not defined!"))
    }

  private def addKeyToStartOfList(key: K): IO[Error, Unit] =
    for {
      oldOptionStart <- self.startRef.get
      _ <- getExistingCacheItem(key).flatMap { cacheItem =>
            self.itemsRef.update(_.updated(key, cacheItem.copy(left = None, right = oldOptionStart)))
          }
      _ <- oldOptionStart match {
            case Some(oldStart) =>
              getExistingCacheItem(oldStart).flatMap { oldStartCacheItem =>
                self.itemsRef.update(_.updated(oldStart, oldStartCacheItem.copy(left = Some(key))))
              }
            case None => ZIO.unit
          }
      _ <- self.startRef.set(Some(key))
      _ <- self.endRef.updateSome { case None => Some(key) }
    } yield ()

  private def removeKeyFromList(key: K): IO[Error, Unit] =
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

  private def updateLeftAndRightCacheItems(l: K, r: K): IO[Error, Unit] =
    for {
      leftCacheItem  <- getExistingCacheItem(l)
      rightCacheItem <- getExistingCacheItem(r)
      _              <- self.itemsRef.update(_.updated(l, leftCacheItem.copy(right = Some(r))))
      _              <- self.itemsRef.update(_.updated(r, rightCacheItem.copy(left = Some(l))))
    } yield ()

  private def setNewEnd(newEnd: K): IO[Error, Unit] =
    for {
      cacheItem <- getExistingCacheItem(newEnd)
      _         <- self.itemsRef.update(_.updated(newEnd, cacheItem.copy(right = None))) *> self.endRef.set(Some(newEnd))
    } yield ()

  private def setNewStart(newStart: K): IO[Error, Unit] =
    for {
      cacheItem <- getExistingCacheItem(newStart)
      _         <- self.itemsRef.update(_.updated(newStart, cacheItem.copy(left = None))) *> self.startRef.set(Some(newStart))
    } yield ()

  private val clearStartAndEnd: UIO[Unit] = self.startRef.set(None) *> self.endRef.set(None)

  private def getExistingCacheItem(key: K): IO[Error, CacheItem[K, V]] =
    ZIO.require(new Error(s"Key does not exist: $key"))(self.itemsRef.get.map(_.get(key)))
}

object LRUCache {
  def make[K, V](capacity: Int): IO[IllegalArgumentException, LRUCache[K, V]] =
    if (capacity > 0) {
      for {
        itemsRef <- Ref.make(Map.empty[K, CacheItem[K, V]])
        startRef <- Ref.make(Option.empty[K])
        endRef   <- Ref.make(Option.empty[K])
      } yield new LRUCache[K, V](capacity, itemsRef, startRef, endRef)
    } else {
      ZIO.fail(new IllegalArgumentException("Capacity must be a positive number!"))
    }
}
