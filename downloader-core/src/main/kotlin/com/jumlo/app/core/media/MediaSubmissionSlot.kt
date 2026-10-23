package com.jumlo.app.core.media

/** Keeps the complete prepared request when persistence succeeds but enqueue reports an error. */
class MediaSubmissionSlot<T> {
    private var signature: String? = null
    private var value: T? = null
    @Synchronized fun existing(signature:String):T? = if(this.signature==signature) value else null
    @Synchronized fun prepare(signature:String, factory:()->T):T {
        if(this.signature==signature) value?.let { return it }
        val created=factory()
        this.signature=signature
        value=created
        return created
    }
    @Synchronized fun clear() { signature=null;value=null }
}
