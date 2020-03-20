package com.example.cache

final case class CacheItem[K, V](value: V, left: Option[K], right: Option[K])
