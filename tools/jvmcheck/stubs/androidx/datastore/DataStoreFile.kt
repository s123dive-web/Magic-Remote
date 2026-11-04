package androidx.datastore
import java.io.File
fun android.content.Context.dataStoreFile(fileName: String): File = File(filesDir, "datastore/$fileName")
