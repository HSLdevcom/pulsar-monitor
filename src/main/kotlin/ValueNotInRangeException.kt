import java.net.URL

class ValueNotInRangeException internal constructor(
    val valueNotInRange: ValueToCheck,
    val actualValue: Double,
    val url: URL
) :
    Exception("At: " + url + ", " + valueNotInRange.path + ": " + actualValue + " is not in range " + valueNotInRange.min + " - " +  valueNotInRange.max)