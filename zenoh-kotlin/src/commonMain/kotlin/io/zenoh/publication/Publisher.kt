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

package io.zenoh.publication

import io.zenoh.*
import io.zenoh.exceptions.SessionException
import io.zenoh.jni.JNIPublisher
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.prelude.Priority
import io.zenoh.prelude.CongestionControl
import io.zenoh.prelude.QoS
import io.zenoh.protocol.ZBytes
import io.zenoh.value.Value

/**
 * # Publisher
 *
 * A Zenoh Publisher.
 *
 * A publisher is automatically dropped when using it with the 'try-with-resources' statement (i.e. 'use' in Kotlin).
 * The session from which it was declared will also keep a reference to it and undeclare it once the session is closed.
 *
 * In order to declare a publisher, [Session.declarePublisher] must be called, which returns a [Publisher.Builder] from
 * which we can specify the [Priority], and the [CongestionControl].
 *
 * Example:
 * ```kotlin
 * val keyExpr = "demo/kotlin/greeting"
 * Session.open().onSuccess {
 *     it.use { session ->
 *         session
 *             .declarePublisher(keyExpr)
 *             .onSuccess { pub ->
 *                 var i = 0
 *                 while (true) {
 *                     pub.put("Hello for the ${i}th time!")
 *                     Thread.sleep(1000)
 *                     i++
 *                 }
 *             }
 *     }
 * }
 * ```
 *
 * The publisher configuration parameters can be later changed using the setter functions.
 *
 * ## Lifespan
 *
 * Internally, the [Session] from which the [Publisher] was declared keeps a reference to it, therefore keeping it alive
 * until the session is closed. For the cases where we want to stop the publisher earlier, it's necessary
 * to keep a reference to it in order to undeclare it later.
 *
 * @property keyExpr The key expression the publisher will be associated to.
 * @property qos [QoS] configuration of the publisher.
 * @property jniPublisher Delegate class handling the communication with the native code.
 * @constructor Create empty Publisher with the default configuration.
 */
class Publisher internal constructor(
    val keyExpr: KeyExpr,
    val qos: QoS,
    private var jniPublisher: JNIPublisher?,
) : SessionDeclaration, AutoCloseable {

    companion object {
        private val InvalidPublisherResult = Result.failure<Unit>(SessionException("Publisher is not valid."))
    }

    val congestionControl = qos.congestionControl
    val priority = qos.priority
    val express = qos.express

    /** Performs a PUT operation on the specified [keyExpr] with the specified [value]. */
    fun put(value: Value, attachment: ZBytes? = null) = jniPublisher?.put(value, attachment) ?: InvalidPublisherResult


    /** Performs a PUT operation on the specified [keyExpr] with the specified string [value]. */
    fun put(value: String, attachment: ZBytes? = null) = jniPublisher?.put(Value(value), attachment) ?: InvalidPublisherResult

    /**
     * Performs a DELETE operation on the specified [keyExpr]
     */
    fun delete(attachment: ZBytes? = null) = jniPublisher?.delete(attachment) ?: InvalidPublisherResult

    fun isValid(): Boolean {
        return jniPublisher != null
    }

    override fun close() {
        undeclare()
    }

    override fun undeclare() {
        jniPublisher?.close()
        jniPublisher = null
    }
}

