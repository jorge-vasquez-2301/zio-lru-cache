package com.example

case class CacheItem[K, V](value: V, left: Option[K], right: Option[K])
