//
// Copyright (c) 2023 ZettaScale Technology
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License 2.0 which is available at
// http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
//
// Contributors:
//   ZettaScale Zenoh Team, <zenoh@zettascale.tech>
//

package io.zenoh

import io.zenoh.jni.JNIZBytes
import io.zenoh.protocol.ZBytes
import io.zenoh.protocol.into
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.reflect.typeOf
import kotlin.test.*

class ZBytesTest {

    @Test
    fun deserializeStringTest() {
        val bytes: ZBytes = "Hello world".into()
        val deserialized = bytes.deserialize<String>().getOrThrow()
        assertEquals("Hello world", deserialized)
    }

    @Test
    fun deserializeByteTest() {
        val bytes: ZBytes = 42.toByte().into()
        val deserialized = bytes.deserialize<Byte>().getOrThrow()
        assertEquals(42, deserialized)
    }

    @Test
    fun deserializeShortTest() {
        val bytes: ZBytes = 3114.toShort().into()
        val deserialized = bytes.deserialize<Short>().getOrThrow()
        assertEquals(3114, deserialized)
    }

    @Test
    fun deserializeIntTest() {
        val bytes: ZBytes = 199722.into()
        val deserialized = bytes.deserialize<Int>().getOrThrow()
        assertEquals(199722, deserialized)
    }

    @Test
    fun deserializeLongTest() {
        val bytes: ZBytes = 72057594038127658.into()
        val deserialized = bytes.deserialize<Long>().getOrThrow()
        assertEquals(72057594038127658, deserialized)
    }

    @Test
    fun deserializeFloatTest() {
        val pi = 3.141516f
        val bytes: ZBytes = pi.into()
        val deserialized = bytes.deserialize<Float>().getOrThrow()
        assertEquals(pi, deserialized)
    }

    @Test
    fun deserializeDoubleTest() {
        val euler = 2.71828
        val bytes: ZBytes = euler.into()
        val deserialized = bytes.deserialize<Double>().getOrThrow()
        assertEquals(euler, deserialized)
    }

    @Test
    fun serializeAndDeserializeMapViaJNITest() {
        Zenoh.load()
        val originalMap =
            mapOf("key1".toByteArray() to "value1".toByteArray(), "key2".toByteArray() to "value2".toByteArray())
        val bytes = JNIZBytes.serializeIntoMapViaJNI(originalMap)
        val deserializedMap = JNIZBytes.deserializeIntoMapViaJNI(bytes)
        assertTrue(compareByteArrayMaps(originalMap, deserializedMap))
    }

    @Test
    fun serializationAndDeserializationTest() {
        Zenoh.load()
        val originalMap = mapOf("key1" to "value1", "key2" to "value2")
        val bytes = ZBytes.serialize(originalMap.map { (k, v) -> k.into() to v.into() }.toMap()).getOrThrow()
        val deserializedMap = bytes.deserialize<Map<String, String>>().getOrThrow()
        assertEquals(originalMap, deserializedMap)
    }

    @Test
    fun serializationAndDeserialization_stringMapTest() {
        Zenoh.load()
        val originalMap = mapOf("key1" to "value1", "key2" to "value2")
        val bytes = ZBytes.serialize(originalMap).getOrThrow()
        val deserializedMap = bytes.deserialize<Map<String, String>>().getOrThrow()
        assertEquals(originalMap, deserializedMap)
    }

    fun compareByteArrayMaps(
        map1: Map<ByteArray, ByteArray>,
        map2: Map<ByteArray, ByteArray>
    ): Boolean {
        if (map1.size != map2.size) return false

        for ((key1, value1) in map1) {
            val key2 = map2.keys.find { it.contentEquals(key1) }
            if (key2 == null) return false
            if (!map2[key2]!!.contentEquals(value1)) return false
        }
        return true
    }

    @Test
    fun serializeIntoListViaJNITest() {
        Zenoh.load()
        val list = listOf("value1", "value2", "value3")
        val zbytes: ZBytes = JNIZBytes.serializeIntoListViaJNI(list.map { it.toByteArray() }).into()

        val deserializedList = zbytes.deserialize<List<String>>().getOrThrow()
        assertEquals(list, deserializedList)
    }

    @Test
    fun customDeserializerTest() {
        val stringMap = mapOf("key1" to "value1", "key2" to "value2")
        val zbytesMap = stringMap.map { (k, v) -> k.into() to v.into() }.toMap()
        val zbytesListOfPairs = stringMap.map { (k, v) -> k.into() to v.into() }
        val intMap = mapOf(1 to 10, 2 to 20, 3 to 30)
        val zbytesList = listOf(1.into(), 2.into(), 3.into())
        val serializedMap = serializeZBytesMap(zbytesMap)
        val bytes: ZBytes = serializedMap.into()

        val customDeserializers = mapOf(
            typeOf<Map<ZBytes, ZBytes>>() to ::deserializeIntoZBytesMap,
            typeOf<Map<String, String>>() to ::deserializeIntoStringMap,
            typeOf<Map<Int, Int>>() to ::deserializeIntoIntMap,
            typeOf<List<ZBytes>>() to ::deserializeIntoZBytesList,
            typeOf<List<Pair<ZBytes, ZBytes>>>() to ::deserializeIntoListOfPairs,
        )

        val deserializedMap = bytes.deserialize<Map<ZBytes, ZBytes>>(customDeserializers).getOrThrow()
        assertEquals(zbytesMap, deserializedMap)

        val deserializedMap2 = bytes.deserialize<Map<String, String>>(customDeserializers).getOrThrow()
        assertEquals(stringMap, deserializedMap2)

        val intMapBytes = serializeIntoIntMap(intMap).into()
        val deserializedMap3 = intMapBytes.deserialize<Map<Int, Int>>(customDeserializers).getOrThrow()
        assertEquals(intMap, deserializedMap3)

        val serializedZBytesList = serializeZBytesList(zbytesList).into()
        val deserializedList = serializedZBytesList.deserialize<List<ZBytes>>(customDeserializers).getOrThrow()
        assertEquals(zbytesList, deserializedList)

        val serializedZBytesPairList = serializeZBytesMap(zbytesListOfPairs.toMap()).into()
        val deserializedZBytesPairList =
            serializedZBytesPairList.deserialize<List<Pair<ZBytes, ZBytes>>>(customDeserializers).getOrThrow()
        assertEquals(zbytesListOfPairs, deserializedZBytesPairList)
    }


    /*--------- Serializers and deserializers for testing purposes. ----------*/

    private fun serializeZBytesMap(testMap: Map<ZBytes, ZBytes>): ByteArray {
        return testMap.map {
            val key = it.key.bytes
            val keyLength = ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(key.size).array()
            val value = it.value.bytes
            val valueLength =
                ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(value.size).array()
            keyLength + key + valueLength + value
        }.reduce { acc, bytes -> acc + bytes }
    }

    private fun deserializeIntoZBytesMap(serializedMap: ByteArray): Map<ZBytes, ZBytes> {
        var idx = 0
        var sliceSize: Int
        val decodedMap = mutableMapOf<ZBytes, ZBytes>()
        while (idx < serializedMap.size) {
            sliceSize = ByteBuffer.wrap(serializedMap.sliceArray(IntRange(idx, idx + Int.SIZE_BYTES - 1)))
                .order(ByteOrder.LITTLE_ENDIAN).int
            idx += Int.SIZE_BYTES

            val key = serializedMap.sliceArray(IntRange(idx, idx + sliceSize - 1))
            idx += sliceSize

            sliceSize = ByteBuffer.wrap(serializedMap.sliceArray(IntRange(idx, idx + Int.SIZE_BYTES - 1))).order(
                ByteOrder.LITTLE_ENDIAN
            ).int
            idx += Int.SIZE_BYTES

            val value = serializedMap.sliceArray(IntRange(idx, idx + sliceSize - 1))
            idx += sliceSize

            decodedMap[key.into()] = value.into()
        }
        return decodedMap
    }

    private fun serializeIntoIntMap(intMap: Map<Int, Int>): ByteArray {
        val zBytesMap = intMap.map { (k, v) -> k.into() to v.into() }.toMap()
        return serializeZBytesMap(zBytesMap)
    }

    private fun deserializeIntoStringMap(serializerMap: ByteArray): Map<String, String> {
        return deserializeIntoZBytesMap(serializerMap).map { (k, v) -> k.toString() to v.toString() }.toMap()
    }

    private fun deserializeIntoIntMap(serializerMap: ByteArray): Map<Int, Int> {
        return deserializeIntoZBytesMap(serializerMap).map { (k, v) ->
            k.deserialize<Int>().getOrThrow() to v.deserialize<Int>().getOrThrow()
        }.toMap()
    }

    private fun serializeZBytesList(list: List<ZBytes>): ByteArray {
        return list.map {
            val item = it.bytes
            val itemLength =
                ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(item.size).array()
            itemLength + item
        }.reduce { acc, bytes -> acc + bytes }
    }

    private fun deserializeIntoZBytesList(serializedList: ByteArray): List<ZBytes> {
        var idx = 0
        var sliceSize: Int
        val decodedList = mutableListOf<ZBytes>()
        while (idx < serializedList.size) {
            sliceSize = ByteBuffer.wrap(serializedList.sliceArray(IntRange(idx, idx + Int.SIZE_BYTES - 1)))
                .order(ByteOrder.LITTLE_ENDIAN).int
            idx += Int.SIZE_BYTES

            val item = serializedList.sliceArray(IntRange(idx, idx + sliceSize - 1))
            idx += sliceSize

            decodedList.add(item.into())
        }
        return decodedList
    }

    private fun deserializeIntoListOfPairs(serializedList: ByteArray): List<Pair<ZBytes, ZBytes>> {
        return deserializeIntoZBytesMap(serializedList).map { (k, v) -> k to v }
    }
}