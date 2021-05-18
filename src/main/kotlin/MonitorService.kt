import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.StandardOpenOption
import kotlin.properties.Delegates


object MonitorService{

    private val f = JsonFactory()
    private val mapper: ObjectMapper = ObjectMapper(f).registerModule(KotlinModule())
    private val EXCEPTION_FILE = "exceptions.txt"
    private val NEW = "new"
    private val REMAINING = "remaining"
    private val GONE = "gone"
    private var monitorConf : MonitorConf? = null

    fun start()= runBlocking {
        val exceptionList = mutableListOf<ValueNotInRange>()
        try{
            monitorConf = mapper.readValue(File("monitor.json"))

            monitorConf!!.endpoints.forEach {
                endpointToCheck ->
                exceptionList.addAll(checkIfValuesAreWithinRange(URL(monitorConf!!.host + "/" + endpointToCheck.endpoint), endpointToCheck.valuesToCheck))
            }
            val exceptionMap = compareExceptionListWithPreviousReport(exceptionList)
            if(exceptionMap[NEW]?.isNotEmpty() == true || exceptionMap[GONE]?.isNotEmpty() == true){
                sendErrorMessageToSlack(exceptionMap, monitorConf!!)
            }
            mapper.writeValue(File(EXCEPTION_FILE), exceptionList)
            //Files.write(File(EXCEPTION_FILE).toPath(), mapper.writeValueAsString(exceptionList), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)

        }
        catch (e: Exception){
            sendUnexpectedErrorMessageToSlack(e, monitorConf!!)
        }
    }

    private fun compareExceptionListWithPreviousReport(currentExceptionlist : List<ValueNotInRange>) : MutableMap<String, List<ValueNotInRange>>{
        val pastExceptionsFile = File(EXCEPTION_FILE)
        val exceptionsMap = mutableMapOf<String, List<ValueNotInRange>>()
        if(pastExceptionsFile.exists()){
            val oldExceptions: List<ValueNotInRange> = mapper.readValue(pastExceptionsFile, object : TypeReference<List<ValueNotInRange>>() {})
            val goneExceptions = oldExceptions.filterNot{currentExceptionlist.contains(it)}
            val newExceptions = currentExceptionlist.filterNot{ oldExceptions.contains(it)}
            val remainingExceptions = oldExceptions.filter{currentExceptionlist.contains(it)}
            exceptionsMap[NEW] = newExceptions
            exceptionsMap[GONE] = goneExceptions
            exceptionsMap[REMAINING] = remainingExceptions
        }
        else{
            exceptionsMap[NEW] = currentExceptionlist
        }
        return exceptionsMap
    }


    private fun sendMessageToSlack(string : String, monitorConf : MonitorConf){
        val url = URL(monitorConf.slackbridge)
        val headers : Map<String, String> = mapOf(
            Pair("Content-type","application/json")
        )

        NetworkHelper.getResponse(url, headers, string)
    }

    private fun sendErrorMessageToSlack(exceptionMap : Map<String, List<ValueNotInRange>>, monitorConf : MonitorConf){
        val message : String = monitorConf.errormessage.format(exceptionMap[NEW]?.joinToString( separator= "\n", transform = {it.toString()} ), exceptionMap[GONE]?.joinToString( separator= "\n", transform = {it.toString()}),exceptionMap[REMAINING]?.joinToString( separator= "\n", transform = {it.toString()}))
        sendMessageToSlack(message, monitorConf)
    }

    private fun sendUnexpectedErrorMessageToSlack(e : Exception, monitorConf : MonitorConf){
        sendMessageToSlack(monitorConf.unexpectederrormessage + " " +  e.message, monitorConf)
        throw e
    }

    /**
     * Throws an exception if a value is out of range
     */
    private suspend fun checkIfValuesAreWithinRange(url : URL, valuesToCheck : List<ValueToCheck>) : List<ValueNotInRange>{
        val exceptionList = mutableListOf<ValueNotInRange>()
        withContext(Dispatchers.IO) {
            val stream = NetworkHelper.getInputStream(url)
            val rootNode: JsonNode = mapper.readValue(stream!!)
            valuesToCheck.forEach { item ->
                    val valueNotInRange = checkIfValueIsWithinRange(rootNode, item, url, 1000)
                    if(valueNotInRange != null){
                        exceptionList.add(valueNotInRange)
                    }
            }
        }
        return exceptionList
    }

    private suspend fun checkIfValueIsWithinRange(data : JsonNode, valueToCheck : ValueToCheck, url : URL, retryDelay : Long) : ValueNotInRange?{
        print("checking $url ${valueToCheck.path} \n")
        var value by Delegates.notNull<Double>()
        try{
            value = data.findValue(valueToCheck.path).asDouble()
        }
        catch(e : Exception){
            throw Exception("Unable to read value from $url path ${valueToCheck.path}", e)
        }
        if( valueToCheck.min != null && value < valueToCheck.min || valueToCheck.max != null && value > valueToCheck.max){
            if(retryDelay == 0L){
                return ValueNotInRange(valueToCheck, value, url)
            }
            else{
                delay(retryDelay)
                return checkIfValueIsWithinRange(data, valueToCheck, url, 0L)
            }
        }
        return null
    }
}

