package com.dahham.jobwriter

import androidx.room.TypeConverter
import java.math.BigDecimal
import java.util.*

class Converters {

    @TypeConverter
    fun fromTimeStamp(value: Long): Date {
        return Date(value)
    }

    @TypeConverter
    fun toTimeSamp(date: Date): Long {
        return date.time
    }

    @TypeConverter
    fun bigDecimalToString(bigDecimal: BigDecimal): String{
        return bigDecimal.toString()
    }

    @TypeConverter
    fun stringToBigDecimal(string: String): BigDecimal{
        return string.toBigDecimal()
    }
}