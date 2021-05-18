import java.net.URL

class ValueNotInRange(
    val valueNotInRange: ValueToCheck,
    val actualValue: Double,
    val url: URL
){

    override fun equals(other: Any?): Boolean {
        return other != null &&
                other is ValueNotInRange &&
                other.url == this.url &&
                other.valueNotInRange.path == this.valueNotInRange.path
    }

    override fun toString() : String{
        return "$url: ${valueNotInRange.path}: $actualValue is not in range ${valueNotInRange.min} -  ${valueNotInRange.max}"
    }
}

