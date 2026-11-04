package androidx.activity.result.contract
abstract class ActivityResultContract<I, O>
object ActivityResultContracts {
    class RequestPermission : ActivityResultContract<String, Boolean>()
    class CreateDocument(mimeType: String) : ActivityResultContract<String, android.net.Uri?>()
    class OpenDocument : ActivityResultContract<Array<String>, android.net.Uri?>()
}
