/*
 * Copyright 2025-2026 Mateusz Kubuszok
 * Licensed under the Apache License, Version 2.0
 */
package lowlevel
package util

import scala.language.implicitConversions
import java.lang.{ Integer => JInt }

class OrderedMapTest extends munit.FunSuite {

  test("empty map") {
    val map = OrderedMap[String, JInt]()
    assertEquals(map.size, 0)
    assert(map.isEmpty)
  }

  test("put and get") {
    val map = OrderedMap[String, JInt]()
    map.put("a", JInt.valueOf(1))
    assertEquals(map.get("a").get.intValue(), 1)
    assertEquals(map.size, 1)
  }

  test("put overwrites previous value") {
    val map = OrderedMap[String, JInt]()
    map.put("a", JInt.valueOf(1))
    val old = map.put("a", JInt.valueOf(2))
    assertEquals(old.get.intValue(), 1)
    assertEquals(map.get("a").get.intValue(), 2)
    assertEquals(map.size, 1)
  }

  test("remove") {
    val map = OrderedMap[String, JInt]()
    map.put("a", JInt.valueOf(1))
    map.put("b", JInt.valueOf(2))
    val removed = map.remove("a")
    assertEquals(removed.get.intValue(), 1)
    assert(!map.containsKey("a"))
    assertEquals(map.size, 1)
  }

  test("containsKey and containsValue") {
    val map = OrderedMap[String, JInt]()
    map.put("x", JInt.valueOf(42))
    assert(map.containsKey("x"))
    assert(map.containsValue(JInt.valueOf(42), false))
    assert(!map.containsKey("y"))
    assert(!map.containsValue(JInt.valueOf(99), false))
  }

  test("clear") {
    val map = OrderedMap[String, JInt]()
    map.put("a", JInt.valueOf(1))
    map.put("b", JInt.valueOf(2))
    map.clear()
    assertEquals(map.size, 0)
    assert(map.isEmpty)
  }

  test("insertion order preserved") {
    val map = OrderedMap[String, JInt]()
    map.put("c", JInt.valueOf(3))
    map.put("a", JInt.valueOf(1))
    map.put("b", JInt.valueOf(2))
    val keys = map.orderedKeys
    assertEquals(keys(0), "c")
    assertEquals(keys(1), "a")
    assertEquals(keys(2), "b")
  }

  test("insertion order preserved after remove") {
    val map = OrderedMap[String, JInt]()
    map.put("a", JInt.valueOf(1))
    map.put("b", JInt.valueOf(2))
    map.put("c", JInt.valueOf(3))
    map.remove("b")
    val keys = map.orderedKeys
    assertEquals(keys.size, 2)
    assertEquals(keys(0), "a")
    assertEquals(keys(1), "c")
  }

  test("removeIndex") {
    val map = OrderedMap[String, JInt]()
    map.put("a", JInt.valueOf(1))
    map.put("b", JInt.valueOf(2))
    map.put("c", JInt.valueOf(3))
    map.removeIndex(1) // remove "b"
    assertEquals(map.size, 2)
    assert(!map.containsKey("b"))
  }

  test("get with default") {
    val map = OrderedMap[String, JInt]()
    assertEquals(map.get("missing", JInt.valueOf(99)).intValue(), 99)
  }

  test("putAll from another OrderedMap") {
    val map1 = OrderedMap[String, JInt]()
    map1.put("a", JInt.valueOf(1))
    map1.put("b", JInt.valueOf(2))
    val map2 = OrderedMap[String, JInt]()
    map2.put("c", JInt.valueOf(3))
    map2.putAll(map1)
    assertEquals(map2.size, 3)
    assert(map2.containsKey("a"))
    assert(map2.containsKey("b"))
    assert(map2.containsKey("c"))
  }

  test("many elements maintain order") {
    val map = OrderedMap[JInt, String]()
    for (i <- 0 until 100)
      map.put(JInt.valueOf(i), s"val$i")
    val keys = map.orderedKeys
    for (i <- 0 until 100)
      assertEquals(keys(i), JInt.valueOf(i))
  }
}
