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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.long
import io.zenoh.bytes.ZBytes
import io.zenoh.handlers.Handler
import io.zenoh.sample.SampleKind
import io.zenoh.query.QueryTarget
import io.zenoh.query.Reply
import io.zenoh.query.Selector
import io.zenoh.query.intoSelector
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import java.time.Duration

class ZGet(private val emptyArgs: Boolean) : CliktCommand(
    help = "Zenoh Get example"
) {

    override fun run() {
        val config = loadConfig(emptyArgs, configFile, connect, listen, noMulticastScouting, mode)

        Zenoh.initLogFromEnvOr("error")

        val session = Zenoh.open(config).getOrThrow()
        val selector = selector.intoSelector().getOrThrow()

        // Run the Get query through one of the examples below:
        runChannelExample(session, selector)
        // runCallbackExample(session, selector)
        // runHandlerExample(session, selector)

        session.close()
    }

    private fun runChannelExample(session: Session, selector: Selector) {
        val channelReceiver = session.get(selector,
            channel = Channel(),
            payload = payload?.let { ZBytes.from(it) },
            target = target?.let { QueryTarget.valueOf(it.uppercase()) } ?: QueryTarget.BEST_MATCHING,
            attachment = attachment?.let { ZBytes.from(it) },
            timeout = Duration.ofMillis(timeout)
        ).getOrThrow()
        runBlocking {
            for (reply in channelReceiver) {
                reply.result.onSuccess { sample ->
                    when (sample.kind) {
                        SampleKind.PUT -> println("Received ('${sample.keyExpr}': '${sample.payload}')")
                        SampleKind.DELETE -> println("Received (DELETE '${sample.keyExpr}')")
                    }
                }.onFailure { error ->
                    println("Received (ERROR: '${error.message}')")
                }
            }
        }
    }

    private fun runCallbackExample(session: Session, selector: Selector) {
        session.get(selector,
            payload = payload?.let { ZBytes.from(it) },
            target = target?.let { QueryTarget.valueOf(it.uppercase()) } ?: QueryTarget.BEST_MATCHING,
            attachment = attachment?.let { ZBytes.from(it) },
            timeout = Duration.ofMillis(timeout),
            callback = { reply ->
                reply.result.onSuccess { sample ->
                    when (sample.kind) {
                        SampleKind.PUT -> println("Received ('${sample.keyExpr}': '${sample.payload}')")
                        SampleKind.DELETE -> println("Received (DELETE '${sample.keyExpr}')")
                    }
                }.onFailure { error ->
                    println("Received (ERROR: '${error.message}')")
                }
            },
        ).getOrThrow()
    }

    private fun runHandlerExample(session: Session, selector: Selector) {
        // Create your own handler implementation
        class ExampleHandler : Handler<Reply, Unit> {
            override fun handle(t: Reply) {
                t.result.onSuccess { sample ->
                    when (sample.kind) {
                        SampleKind.PUT -> println("Received ('${sample.keyExpr}': '${sample.payload}')")
                        SampleKind.DELETE -> println("Received (DELETE '${sample.keyExpr}')")
                    }
                }.onFailure { error ->
                    println("Received (ERROR: '${error.message}')")
                }
            }

            override fun receiver() {}
            override fun onClose() {}
        }

        session.get(selector,
            payload = payload?.let { ZBytes.from(it) },
            target = target?.let { QueryTarget.valueOf(it.uppercase()) } ?: QueryTarget.BEST_MATCHING,
            attachment = attachment?.let { ZBytes.from(it) },
            timeout = Duration.ofMillis(timeout),
            handler = ExampleHandler(), // Provide a handler instance
        ).getOrThrow()
    }

    private val selector by option(
        "-s",
        "--selector",
        help = "The selection of resources to query [default: demo/example/**]",
        metavar = "selector"
    ).default("demo/example/**")
    private val payload by option(
        "-p", "--payload", help = "An optional payload to put in the query.", metavar = "payload"
    )
    private val target by option(
        "-t",
        "--target",
        help = "The target queryables of the query. Default: BEST_MATCHING. " + "[possible values: BEST_MATCHING, ALL, ALL_COMPLETE]",
        metavar = "target"
    )
    private val timeout by option(
        "-o", "--timeout", help = "The query timeout in milliseconds [default: 10000]", metavar = "timeout"
    ).long().default(10000)
    private val configFile by option("-c", "--config", help = "A configuration file.", metavar = "config")
    private val mode by option(
        "-m",
        "--mode",
        help = "The session mode. Default: peer. Possible values: [peer, client, router]",
        metavar = "mode"
    ).default("peer")
    private val connect: List<String> by option(
        "-e", "--connect", help = "Endpoints to connect to.", metavar = "connect"
    ).multiple()
    private val listen: List<String> by option(
        "-l", "--listen", help = "Endpoints to listen on.", metavar = "listen"
    ).multiple()
    private val attachment by option(
        "-a",
        "--attach",
        help = "The attachment to add to the get. The key-value pairs are &-separated, and = serves as the separator between key and value.",
        metavar = "attach"
    )
    private val noMulticastScouting: Boolean by option(
        "--no-multicast-scouting", help = "Disable the multicast-based scouting mechanism."
    ).flag(default = false)
}

fun main(args: Array<String>) = ZGet(args.isEmpty()).main(args)
