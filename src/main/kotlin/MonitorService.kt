import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.omg.CORBA.Environment
import java.io.File
import java.net.URL
import kotlin.properties.Delegates


object MonitorService{

    private val f = JsonFactory()
    private val mapper: ObjectMapper = ObjectMapper(f).registerModule(KotlinModule())

    fun start()= runBlocking {
        var monitorConf : MonitorConf? = null
        try{
            monitorConf = mapper.readValue(File("monitor.json"))
            monitorConf!!.endpoints.forEach {
                endpointToCheck ->  checkIfValuesAreWithinRange(URL(monitorConf.host + "/" + endpointToCheck.endpoint), endpointToCheck.valuesToCheck)
            }
        }
        catch (e: Exception){
            sendErrorMessageToSlack(e, monitorConf!!)
        }
    }

    private fun sendErrorMessageToSlack(e : Exception, monitorConf : MonitorConf){

        val url = URL(monitorConf.slackbridge)
        val headers : Map<String, String> = mapOf(
            Pair("Content-type","application/json")
        )

        NetworkHelper.getResponse(url, headers, monitorConf.errormessage + " " +  e.message)
        throw e
    }

    /**
     * Throws an exception if a value is out of range
     */
    private suspend fun checkIfValuesAreWithinRange(url : URL, valuesToCheck : List<ValueToCheck>){
        withContext(Dispatchers.IO) {
            val stream = NetworkHelper.getInputStream(url)
            val rootNode: JsonNode = mapper.readValue(stream!!)
            valuesToCheck.forEach { item -> checkIfValueIsWithinRange(rootNode, item, url) }
        }
    }

    private fun checkIfValueIsWithinRange(data : JsonNode, valueToCheck : ValueToCheck, url : URL){
        var value by Delegates.notNull<Double>()
        try{
            value = data.findValue(valueToCheck.path).asDouble()
        }
        catch(e : Exception){
            throw Exception("Unable to read value from $url path ${valueToCheck.path}", e)
        }
        if( valueToCheck.min != null && value < valueToCheck.min || valueToCheck.max != null && value > valueToCheck.max){
            throw ValueNotInRangeException(valueToCheck, value, url)
        }
    }
}

