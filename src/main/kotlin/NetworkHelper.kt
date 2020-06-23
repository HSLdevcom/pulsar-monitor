import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.InputStream
import java.net.URL
import java.time.LocalDateTime


object NetworkHelper {
	
	private val okHttpClient = OkHttpClient()

	@Throws(Exception::class)
	fun getResponse(url : URL, headers : Map<String,String>?, body : String? = null) : Response{
		val requestBuilder = Request.Builder().url(url)
		headers?.entries?.forEach { entry ->
			requestBuilder.addHeader(entry.key, entry.value)
		}
		if(body != null) requestBuilder.post(body.toRequestBody())
		return okHttpClient.newCall(requestBuilder.build()).execute()
	}

	@Throws(Exception::class)
	fun getInputStream(url : URL, headers : Map<String,String>? = null) : InputStream?{
		val response = getResponse(url, headers)
		if(headers != null && headers.containsKey(("If-None-Match")) && headers.get("If-None-Match") == response.header("ETag")){
			response.body?.close()
			return null
		}
		return if (response.body!!.contentLength() != 0L) response.body?.byteStream() else null
	}
}