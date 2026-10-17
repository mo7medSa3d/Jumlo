package com.jumlo.app.data.source

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EphemeralSecretStoreTest {

    @Test
    fun `consume returns the secret once then nothing`() {
        val store = EphemeralSecretStore()
        val handle = store.put("s3cr3t".toCharArray())
        assertArrayEquals("s3cr3t".toCharArray(), store.consume(handle))
        assertNull(store.consume(handle))
    }

    @Test
    fun `put copies input so caller mutation does not leak in`() {
        val store = EphemeralSecretStore()
        val input = "s3cr3t".toCharArray()
        val handle = store.put(input)
        input.fill('x')
        assertArrayEquals("s3cr3t".toCharArray(), store.consume(handle))
    }

    @Test
    fun `remove and clear drop secrets without returning them twice`() {
        val store = EphemeralSecretStore()
        val keep = store.put("keep".toCharArray())
        val drop = store.put("drop".toCharArray())
        store.remove(drop)
        assertNull(store.consume(drop))
        store.clear()
        assertNull(store.consume(keep))
    }

    @Test
    fun `handles are unique per put`() {
        val store = EphemeralSecretStore()
        val first = store.put("same".toCharArray())
        val second = store.put("same".toCharArray())
        assertNotEquals(first, second)
    }
}
