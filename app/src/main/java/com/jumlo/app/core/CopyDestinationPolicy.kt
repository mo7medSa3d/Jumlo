package com.jumlo.app.core

import com.jumlo.app.model.CollisionPolicy

/** Resolve a destination before any provider write or source deletion. */
object CopyDestinationPolicy {
    fun name(sourceIdentity:String,desiredName:String,existing:List<Pair<String,String>>,move:Boolean,policy:CollisionPolicy):String? {
        val base=SafeNames.basename(desiredName)
        val match=existing.firstOrNull { it.second.equals(base,ignoreCase=true) }
        // A copy may share its requested name with the source: KEEP_BOTH resolves a new
        // name before provider creation. A move back onto the same document is rejected.
        require(!move || match?.first!=sourceIdentity) { "SOURCE_DESTINATION_IDENTICAL" }
        return when {
            match==null -> base
            policy==CollisionPolicy.SKIP -> null
            policy==CollisionPolicy.REPLACE -> throw IllegalArgumentException("PROVIDER_ATOMIC_REPLACE_UNAVAILABLE")
            else -> SafeNames.unique(existing.map { it.second }.toSet(),base)
        }
    }
}
