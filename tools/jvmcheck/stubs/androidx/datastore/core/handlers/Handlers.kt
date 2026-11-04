package androidx.datastore.core.handlers
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.CorruptionHandler
class ReplaceFileCorruptionHandler<T>(val produceNewData: (CorruptionException) -> T) : CorruptionHandler<T>
