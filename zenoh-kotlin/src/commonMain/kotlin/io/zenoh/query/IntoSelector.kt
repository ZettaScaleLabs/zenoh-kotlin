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

package io.zenoh.query

import io.zenoh.exceptions.ZError
import io.zenoh.keyexpr.KeyExpr

fun String.intoSelector(): Result<Selector> = runCatching {
    if (this.isEmpty()) {
        throw ZError("Attempting to create a KeyExpr from an empty string.")
    }
    val result = this.split('?', limit = 2)
    val keyExpr = KeyExpr.autocanonize(result[0]).getOrThrow()
    val params = if (result.size == 2) Parameters.from(result[1]).getOrThrow() else null

    Selector(keyExpr, params)
}
